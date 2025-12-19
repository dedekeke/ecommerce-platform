package com.ecommerce.mediaservice.service;

import com.ecommerce.mediaservice.document.Media;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Service for image processing operations (resizing, thumbnail generation)
 */
@Service
public class ImageProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(ImageProcessingService.class);

    private final int thumbnailWidth;
    private final int thumbnailHeight;

    public ImageProcessingService(
            @Value("${media.thumbnail.width}") int thumbnailWidth,
            @Value("${media.thumbnail.height}") int thumbnailHeight) {
        this.thumbnailWidth = thumbnailWidth;
        this.thumbnailHeight = thumbnailHeight;
    }

    /**
     * Create a thumbnail from the source image
     * Maintains aspect ratio and doesn't enlarge small images
     */
    public void createThumbnail(Path sourcePath, Path outputPath) {
        try {
            logger.debug("Creating thumbnail from {} to {}", sourcePath, outputPath);

            // Read the original image to check dimensions
            BufferedImage originalImage = ImageIO.read(sourcePath.toFile());
            if (originalImage == null) {
                throw new RuntimeException("Failed to read source image");
            }

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();

            // If image is already smaller than thumbnail size, just copy it
            if (originalWidth <= thumbnailWidth && originalHeight <= thumbnailHeight) {
                logger.debug("Image is already small enough, copying without resize");
                Thumbnails.of(sourcePath.toFile())
                        .scale(1.0) // Keep original size
                        .toFile(outputPath.toFile());
            } else {
                // Resize to thumbnail size
                Thumbnails.of(sourcePath.toFile())
                        .size(thumbnailWidth, thumbnailHeight)
                        .keepAspectRatio(true)
                        .toFile(outputPath.toFile());
            }

            logger.info("Created thumbnail: {}", outputPath.getFileName());

        } catch (IOException e) {
            logger.error("Failed to create thumbnail for {}", sourcePath, e);
            throw new RuntimeException("Failed to create thumbnail", e);
        }
    }

    /**
     * Get image dimensions from a file
     */
    public Media.ImageDimensions getImageDimensions(Path imagePath) {
        try {
            BufferedImage image = ImageIO.read(imagePath.toFile());

            if (image == null) {
                throw new RuntimeException("Failed to read image: " + imagePath);
            }

            return Media.ImageDimensions.builder()
                    .width(image.getWidth())
                    .height(image.getHeight())
                    .build();

        } catch (IOException e) {
            logger.error("Failed to read image dimensions for {}", imagePath, e);
            throw new RuntimeException("Failed to read image dimensions", e);
        }
    }

    /**
     * Generate thumbnail filename from original filename
     * Example: "image.jpg" -> "thumb_image.jpg"
     */
    public String generateThumbnailFilename(String originalFilename) {
        return "thumb_" + originalFilename;
    }
}
