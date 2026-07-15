package com.ecommerce.mediaservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;


@Service
public class FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

    private final Path storageLocation;
    private final Path thumbnailsLocation;

    public FileStorageService(
            @Value("${media.storage.location}") String storageLocation,
            @Value("${media.storage.thumbnails-location}") String thumbnailsLocation) {
        this.storageLocation = Paths.get(storageLocation).toAbsolutePath().normalize();
        this.thumbnailsLocation = Paths.get(thumbnailsLocation).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(this.storageLocation);
            Files.createDirectories(this.thumbnailsLocation);
            logger.info("Storage directories initialized: {} and {}",
                    storageLocation, thumbnailsLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not create storage directories", e);
        }
    }

    /**
     * Store a file and return the generated filename
     */
    public String store(MultipartFile file) {
        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Failed to store empty file");
        }

        if (file.getOriginalFilename() == null) {
            throw new IllegalArgumentException("Failed to store file with no filename");
        }
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());

        try {
            // Security check: prevent path traversal attacks
            if (originalFilename.contains("..")) {
                throw new RuntimeException(
                        "Cannot store file outside current directory: " + originalFilename);
            }

            // Generate unique filename to avoid conflicts
            String extension = getFileExtension(originalFilename);
            String uniqueFilename = UUID.randomUUID().toString() + extension;

            // Copy file to storage location. Files.copy closes the OutputStream it
            // opens but NOT the source stream, so close it explicitly to avoid a
            // file-descriptor leak under upload load.
            Path targetLocation = this.storageLocation.resolve(uniqueFilename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            }

            logger.info("Stored file: {} as {}", originalFilename, uniqueFilename);
            return uniqueFilename;

        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + originalFilename, e);
        }
    }

    /**
     * Load file as a Resource
     */
    public Resource loadAsResource(String filename) {
        try {
            Path filePath = storageLocation.resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("File not found: " + filename, e);
        }
    }

    /**
     * Delete a file
     */
    public void delete(String filename) {
        try {
            Path filePath = storageLocation.resolve(filename).normalize();
            Files.deleteIfExists(filePath);
            logger.info("Deleted file: {}", filename);
        } catch (IOException e) {
            logger.error("Failed to delete file: {}", filename, e);
            throw new RuntimeException("Failed to delete file: " + filename, e);
        }
    }

    /**
     * Delete a thumbnail
     */
    public void deleteThumbnail(String thumbnailFilename) {
        try {
            Path thumbnailPath = thumbnailsLocation.resolve(thumbnailFilename).normalize();
            Files.deleteIfExists(thumbnailPath);
            logger.info("Deleted thumbnail: {}", thumbnailFilename);
        } catch (IOException e) {
            logger.error("Failed to delete thumbnail: {}", thumbnailFilename, e);
            throw new RuntimeException("Failed to delete thumbnail: " + thumbnailFilename, e);
        }
    }

    /**
     * Get the file path for a stored file
     */
    public Path getFilePath(String filename) {
        return storageLocation.resolve(filename).normalize();
    }

    /**
     * Get the thumbnail path
     */
    public Path getThumbnailPath(String thumbnailFilename) {
        return thumbnailsLocation.resolve(thumbnailFilename).normalize();
    }

    /**
     * Extract file extension from filename
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex);
        }
        return "";
    }
}
