package com.ecommerce.mediaservice.service;

import com.ecommerce.mediaservice.document.Media;
import com.ecommerce.mediaservice.dto.MediaResponse;
import com.ecommerce.mediaservice.exception.MediaNotFoundException;
import com.ecommerce.mediaservice.repository.MediaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing media files and metadata
 */
@Service
public class MediaService {

    private static final Logger logger = LoggerFactory.getLogger(MediaService.class);

    private final MediaRepository mediaRepository;
    private final FileStorageService fileStorageService;
    private final ImageProcessingService imageProcessingService;

    private final List<String> allowedFileTypes;
    private final long maxFileSize;

    public MediaService(
            MediaRepository mediaRepository,
            FileStorageService fileStorageService,
            ImageProcessingService imageProcessingService,
            @Value("${media.allowed-file-types}") String allowedFileTypes,
            @Value("${spring.servlet.multipart.max-file-size:10MB}") String maxFileSizeStr) {
        this.mediaRepository = mediaRepository;
        this.fileStorageService = fileStorageService;
        this.imageProcessingService = imageProcessingService;
        this.allowedFileTypes = Arrays.asList(allowedFileTypes.split(","));
        this.maxFileSize = parseSize(maxFileSizeStr);
    }

    /**
     * Upload a file
     */
    @Transactional
    public MediaResponse uploadFile(MultipartFile file, String uploadedBy) {
        logger.info("Uploading file: {} by user: {}", file.getOriginalFilename(), uploadedBy);

        // Validate file
        validateFile(file);

        try {
            // Store the file
            String storedFilename = fileStorageService.store(file);
            Path filePath = fileStorageService.getFilePath(storedFilename);

            // Build media metadata
            Media.MediaBuilder mediaBuilder = Media.builder()
                    .filename(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .size(file.getSize())
                    .storageUrl("/storage/" + storedFilename)
                    .uploadedBy(uploadedBy);

            // Process image if it's an image file
            if (isImage(file.getContentType())) {
                processImage(filePath, storedFilename, mediaBuilder);
            }

            // Save metadata to MongoDB
            Media savedMedia = mediaRepository.save(mediaBuilder.build());

            logger.info("File uploaded successfully: {} with id: {}", file.getOriginalFilename(), savedMedia.getId());

            return toMediaResponse(savedMedia);

        } catch (Exception e) {
            logger.error("Failed to upload file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Failed to upload file", e);
        }
    }

    /**
     * Get media by ID. Callers may only read their own media unless they hold
     * admin scope; a cross-user attempt is reported as 404 to avoid leaking the
     * existence of another user's media through opaque ids.
     */
    public MediaResponse getMediaById(String id, String requesterId, boolean isAdmin) {
        Media media = findOwnedMedia(id, requesterId, isAdmin);
        return toMediaResponse(media);
    }

    /**
     * Load media file as Resource, enforcing the same ownership rules as
     * {@link #getMediaById(String, String, boolean)}.
     */
    public Resource loadMediaFile(String id, String requesterId, boolean isAdmin) {
        Media media = findOwnedMedia(id, requesterId, isAdmin);

        String filename = extractFilename(media.getStorageUrl());
        return fileStorageService.loadAsResource(filename);
    }

    /**
     * Look up media by id and assert the caller may access it. Non-owners without
     * admin scope get a {@link MediaNotFoundException} (404) rather than a 403 so
     * opaque media ids cannot be enumerated.
     */
    private Media findOwnedMedia(String id, String requesterId, boolean isAdmin) {
        Media media = mediaRepository.findById(id)
                .orElseThrow(() -> new MediaNotFoundException(id));

        if (!isAdmin && !media.getUploadedBy().equals(requesterId)) {
            logger.warn("Access denied: user {} attempted to access media {} owned by {}",
                    requesterId, id, media.getUploadedBy());
            throw new MediaNotFoundException(id);
        }

        return media;
    }

    /**
     * Delete media
     */
    @Transactional
    public void deleteMedia(String id, String userId) {
        deleteMedia(id, userId, false);
    }

    /**
     * Delete media with admin override
     */
    @Transactional
    public void deleteMedia(String id, String userId, boolean isAdmin) {
        logger.info("Deleting media: {} by user: {} (admin: {})", id, userId, isAdmin);

        Media media = mediaRepository.findById(id)
                .orElseThrow(() -> new MediaNotFoundException(id));

        // Check authorization
        if (!isAdmin && !media.getUploadedBy().equals(userId)) {
            throw new SecurityException("User is not authorized to delete this media");
        }

        try {
            // Delete physical files
            String filename = extractFilename(media.getStorageUrl());
            fileStorageService.delete(filename);

            if (media.getThumbnailUrl() != null) {
                String thumbnailFilename = extractFilename(media.getThumbnailUrl());
                fileStorageService.deleteThumbnail(thumbnailFilename);
            }

            // Delete metadata
            mediaRepository.delete(media);

            logger.info("Media deleted successfully: {}", id);

        } catch (Exception e) {
            logger.error("Failed to delete media: {}", id, e);
            throw new RuntimeException("Failed to delete media", e);
        }
    }

    /**
     * Get all media uploaded by a user. The listing is keyed on a caller-supplied
     * userId, so a caller may only list their own uploads unless they hold admin
     * scope; a cross-user attempt is a 403 authorization failure.
     */
    public List<MediaResponse> getMediaByUser(String userId, String requesterId, boolean isAdmin) {
        if (!isAdmin && !userId.equals(requesterId)) {
            logger.warn("Access denied: user {} attempted to list media of user {}", requesterId, userId);
            throw new SecurityException("User is not authorized to list this user's media");
        }

        List<Media> mediaList = mediaRepository.findByUploadedBy(userId);
        return mediaList.stream()
                .map(this::toMediaResponse)
                .collect(Collectors.toList());
    }

    /**
     * Validate file before upload
     */
    private void validateFile(MultipartFile file) {
        // Check file is not empty
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload empty file");
        }

        // Check file type
        String contentType = file.getContentType();
        if (contentType == null || !allowedFileTypes.contains(contentType)) {
            throw new IllegalArgumentException("File type not allowed: " + contentType);
        }

        // Check file size
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException(
                    String.format("File size exceeds maximum allowed size: %d bytes", maxFileSize));
        }
    }

    /**
     * Process image file (create thumbnail and extract dimensions)
     */
    private void processImage(Path imagePath, String storedFilename, Media.MediaBuilder mediaBuilder) {
        try {
            // Generate thumbnail
            String thumbnailFilename = imageProcessingService.generateThumbnailFilename(storedFilename);
            Path thumbnailPath = fileStorageService.getThumbnailPath(thumbnailFilename);

            imageProcessingService.createThumbnail(imagePath, thumbnailPath);
            mediaBuilder.thumbnailUrl("/storage/thumbnails/" + thumbnailFilename);

            // Extract dimensions
            Media.ImageDimensions dimensions = imageProcessingService.getImageDimensions(imagePath);
            mediaBuilder.dimensions(dimensions);

            logger.debug("Image processed successfully: {}", storedFilename);

        } catch (Exception e) {
            logger.warn("Failed to process image, continuing without thumbnail: {}", storedFilename, e);
            // Continue without thumbnail if processing fails
        }
    }

    /**
     * Check if content type is an image
     */
    private boolean isImage(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }

    /**
     * Extract filename from storage URL
     */
    private String extractFilename(String storageUrl) {
        return storageUrl.substring(storageUrl.lastIndexOf('/') + 1);
    }

    /**
     * Convert Media entity to MediaResponse DTO
     */
    private MediaResponse toMediaResponse(Media media) {
        MediaResponse.MediaResponseBuilder builder = MediaResponse.builder()
                .id(media.getId())
                .filename(media.getFilename())
                .contentType(media.getContentType())
                .size(media.getSize())
                .downloadUrl("/api/media/" + media.getId() + "/download")
                .uploadedBy(media.getUploadedBy())
                .createdAt(media.getCreatedAt());

        if (media.getThumbnailUrl() != null) {
            builder.thumbnailUrl(media.getThumbnailUrl());
        }

        if (media.getDimensions() != null) {
            builder.dimensions(MediaResponse.ImageDimensions.builder()
                    .width(media.getDimensions().getWidth())
                    .height(media.getDimensions().getHeight())
                    .build());
        }

        return builder.build();
    }

    /**
     * Parse file size string (e.g., "10MB", "1GB") to bytes
     */
    private long parseSize(String sizeStr) {
        sizeStr = sizeStr.trim().toUpperCase();
        long multiplier = 1;

        if (sizeStr.endsWith("KB")) {
            multiplier = 1024;
            sizeStr = sizeStr.substring(0, sizeStr.length() - 2);
        } else if (sizeStr.endsWith("MB")) {
            multiplier = 1024 * 1024;
            sizeStr = sizeStr.substring(0, sizeStr.length() - 2);
        } else if (sizeStr.endsWith("GB")) {
            multiplier = 1024 * 1024 * 1024;
            sizeStr = sizeStr.substring(0, sizeStr.length() - 2);
        }

        return Long.parseLong(sizeStr.trim()) * multiplier;
    }
}
