package com.ecommerce.mediaservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaResponse {
    private String id;
    private String filename;
    private String contentType;
    private Long size;
    private String downloadUrl;
    private String contentUrl;
    private String thumbnailUrl;
    private ImageDimensions dimensions;
    private String uploadedBy;
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
