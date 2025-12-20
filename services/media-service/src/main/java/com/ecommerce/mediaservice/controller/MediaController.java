package com.ecommerce.mediaservice.controller;

import com.ecommerce.mediaservice.dto.MediaResponse;
import com.ecommerce.mediaservice.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


@RestController
@RequestMapping("/api/media")
@Tag(name = "Media", description = "Media management API")
@SecurityRequirement(name = "bearer-auth")
public class MediaController {

    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file")
    public ResponseEntity<MediaResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();
        logger.info("Upload request from user: {} for file: {}", userId, file.getOriginalFilename());

        MediaResponse response = mediaService.uploadFile(file, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get media metadata by ID")
    public ResponseEntity<MediaResponse> getMedia(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {

        logger.debug("Get media request for id: {}", id);

        MediaResponse response = mediaService.getMediaById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download media file")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {

        logger.debug("Download request for media id: {}", id);

        MediaResponse media = mediaService.getMediaById(id);
        Resource resource = mediaService.loadMediaFile(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + media.getFilename() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete media file")
    public ResponseEntity<Void> deleteMedia(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getSubject();
        logger.info("Delete request from user: {} for media: {}", userId, id);

        mediaService.deleteMedia(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all media for a user")
    public ResponseEntity<List<MediaResponse>> getUserMedia(
            @PathVariable String userId,
            @AuthenticationPrincipal Jwt jwt) {

        logger.debug("Get user media request for userId: {}", userId);

        List<MediaResponse> mediaList = mediaService.getMediaByUser(userId);
        return ResponseEntity.ok(mediaList);
    }
}
