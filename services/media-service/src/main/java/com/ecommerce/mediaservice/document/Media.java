package com.ecommerce.mediaservice.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Media Document - Represents file metadata stored in MongoDB
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "media")
public class Media {

    @Id
    private String id;

    private String filename;

    private String contentType;

    private Long size; // in bytes

    private String storageUrl;

    private String thumbnailUrl;

    private ImageDimensions dimensions;

    private String uploadedBy; // Auth0 user ID

    @CreatedDate
    private LocalDateTime createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageDimensions {
        private Integer width;
        private Integer height;
    }
}
