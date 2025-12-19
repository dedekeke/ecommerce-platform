package com.ecommerce.mediaservice.service;

import com.ecommerce.mediaservice.document.Media;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class ImageProcessingServiceTest {

    @TempDir
    Path tempDir;

    private ImageProcessingService imageProcessingService;
    private int thumbnailWidth;
    private int thumbnailHeight;

    @BeforeEach
    void setUp() {
        thumbnailWidth = 200;
        thumbnailHeight = 200;
        imageProcessingService = new ImageProcessingService(thumbnailWidth, thumbnailHeight);
    }

    @Test
    void shouldCreateThumbnail() throws IOException {
        // Given
        Path testImage = createTestImage(800, 600, "jpeg");
        Path outputPath = tempDir.resolve("thumbnail.jpg");

        // When
        imageProcessingService.createThumbnail(testImage, outputPath);

        // Then
        assertThat(Files.exists(outputPath)).isTrue();

        BufferedImage thumbnail = ImageIO.read(outputPath.toFile());
        assertThat(thumbnail.getWidth()).isLessThanOrEqualTo(thumbnailWidth);
        assertThat(thumbnail.getHeight()).isLessThanOrEqualTo(thumbnailHeight);
    }

    @Test
    void shouldMaintainAspectRatioWhenCreatingThumbnail() throws IOException {
        // Given
        Path testImage = createTestImage(1600, 900, "jpeg"); // 16:9 ratio
        Path outputPath = tempDir.resolve("thumbnail.jpg");

        // When
        imageProcessingService.createThumbnail(testImage, outputPath);

        // Then
        BufferedImage thumbnail = ImageIO.read(outputPath.toFile());

        // Should maintain 16:9 aspect ratio
        double originalRatio = 1600.0 / 900.0;
        double thumbnailRatio = (double) thumbnail.getWidth() / thumbnail.getHeight();

        assertThat(thumbnailRatio).isCloseTo(originalRatio, within(0.1));
    }

    @Test
    void shouldHandlePNGImages() throws IOException {
        // Given
        Path testImage = createTestImage(800, 600, "png");
        Path outputPath = tempDir.resolve("thumbnail.png");

        // When
        imageProcessingService.createThumbnail(testImage, outputPath);

        // Then
        assertThat(Files.exists(outputPath)).isTrue();
        BufferedImage thumbnail = ImageIO.read(outputPath.toFile());
        assertThat(thumbnail).isNotNull();
    }

    @Test
    void shouldNotEnlargeSmallImages() throws IOException {
        // Given - create image smaller than thumbnail size
        Path testImage = createTestImage(100, 75, "jpeg");
        Path outputPath = tempDir.resolve("thumbnail.jpg");

        // When
        imageProcessingService.createThumbnail(testImage, outputPath);

        // Then
        BufferedImage thumbnail = ImageIO.read(outputPath.toFile());

        // Should not enlarge - keep original size
        assertThat(thumbnail.getWidth()).isLessThanOrEqualTo(100);
        assertThat(thumbnail.getHeight()).isLessThanOrEqualTo(75);
    }

    @Test
    void shouldExtractImageDimensions() throws IOException {
        // Given
        Path testImage = createTestImage(1920, 1080, "jpeg");

        // When
        Media.ImageDimensions dimensions = imageProcessingService.getImageDimensions(testImage);

        // Then
        assertThat(dimensions).isNotNull();
        assertThat(dimensions.getWidth()).isEqualTo(1920);
        assertThat(dimensions.getHeight()).isEqualTo(1080);
    }

    @Test
    void shouldHandleNonImageFile() {
        // Given
        Path textFile = tempDir.resolve("text.txt");

        // When/Then
        assertThatThrownBy(() -> {
            Files.writeString(textFile, "not an image");
            imageProcessingService.getImageDimensions(textFile);
        })
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to read image");
    }

    @Test
    void shouldGenerateThumbnailFilename() {
        // Given
        String originalFilename = "image-123.jpg";

        // When
        String thumbnailFilename = imageProcessingService.generateThumbnailFilename(originalFilename);

        // Then
        assertThat(thumbnailFilename).isEqualTo("thumb_image-123.jpg");
    }

    @Test
    void shouldGenerateThumbnailFilenameWithoutExtension() {
        // Given
        String originalFilename = "image-123";

        // When
        String thumbnailFilename = imageProcessingService.generateThumbnailFilename(originalFilename);

        // Then
        assertThat(thumbnailFilename).isEqualTo("thumb_image-123");
    }

    @Test
    void shouldDetectImageContentType() throws IOException {
        // Given
        Path jpegImage = createTestImage(100, 100, "jpeg");
        Path pngImage = createTestImage(100, 100, "png");

        // When
        String jpegType = Files.probeContentType(jpegImage);
        String pngType = Files.probeContentType(pngImage);

        // Then
        assertThat(jpegType).contains("image");
        assertThat(pngType).contains("image");
    }

    // Helper method to create test images
    private Path createTestImage(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Fill with a gradient pattern for testing
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = (x * 255 / width) << 16 | (y * 255 / height) << 8 | 128;
                image.setRGB(x, y, rgb);
            }
        }

        Path imagePath = tempDir.resolve("test-image." + format);
        ImageIO.write(image, format, imagePath.toFile());
        return imagePath;
    }
}
