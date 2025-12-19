package com.ecommerce.mediaservice.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;


class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;
    private Path storageLocation;
    private Path thumbnailsLocation;

    @BeforeEach
    void setUp() throws IOException {
        storageLocation = tempDir.resolve("storage");
        thumbnailsLocation = tempDir.resolve("thumbnails");
        Files.createDirectories(storageLocation);
        Files.createDirectories(thumbnailsLocation);

        fileStorageService = new FileStorageService(
                storageLocation.toString(),
                thumbnailsLocation.toString()
        );
        fileStorageService.init();
    }

    @AfterEach
    void tearDown() throws IOException {
        // Cleanup is handled by @TempDir
    }

    @Test
    void shouldInitializeStorageDirectories() {
        // Then
        assertThat(Files.exists(storageLocation)).isTrue();
        assertThat(Files.exists(thumbnailsLocation)).isTrue();
    }

    @Test
    void shouldStoreFile() throws IOException {
        // Given
        MultipartFile file = new MockMultipartFile(
                "test-image.jpg",
                "test-image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        // When
        String storedFilename = fileStorageService.store(file);

        // Then
        assertThat(storedFilename).isNotNull();
        assertThat(storedFilename).endsWith(".jpg");

        Path storedFile = storageLocation.resolve(storedFilename);
        assertThat(Files.exists(storedFile)).isTrue();
        assertThat(Files.size(storedFile)).isEqualTo("test image content".getBytes().length);
    }

    @Test
    void shouldGenerateUniqueFilenamesForDuplicates() throws IOException {
        // Given
        MultipartFile file1 = new MockMultipartFile(
                "test.jpg",
                "test.jpg",
                "image/jpeg",
                "content1".getBytes()
        );
        MultipartFile file2 = new MockMultipartFile(
                "test.jpg",
                "test.jpg",
                "image/jpeg",
                "content2".getBytes()
        );

        // When
        String filename1 = fileStorageService.store(file1);
        String filename2 = fileStorageService.store(file2);

        // Then
        assertThat(filename1).isNotEqualTo(filename2);
        assertThat(Files.exists(storageLocation.resolve(filename1))).isTrue();
        assertThat(Files.exists(storageLocation.resolve(filename2))).isTrue();
    }

    @Test
    void shouldLoadFileAsResource() throws IOException {
        // Given
        MultipartFile file = new MockMultipartFile(
                "test.jpg",
                "test.jpg",
                "image/jpeg",
                "test content".getBytes()
        );
        String storedFilename = fileStorageService.store(file);

        // When
        org.springframework.core.io.Resource resource = fileStorageService.loadAsResource(storedFilename);

        // Then
        assertThat(resource).isNotNull();
        assertThat(resource.exists()).isTrue();
        assertThat(resource.isReadable()).isTrue();
        assertThat(resource.contentLength()).isEqualTo("test content".getBytes().length);
    }

    @Test
    void shouldThrowExceptionWhenLoadingNonExistentFile() {
        // When/Then
        assertThatThrownBy(() -> fileStorageService.loadAsResource("non-existent.jpg"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("File not found");
    }

    @Test
    void shouldDeleteFile() throws IOException {
        // Given
        MultipartFile file = new MockMultipartFile(
                "test.jpg",
                "test.jpg",
                "image/jpeg",
                "test content".getBytes()
        );
        String storedFilename = fileStorageService.store(file);
        assertThat(Files.exists(storageLocation.resolve(storedFilename))).isTrue();

        // When
        fileStorageService.delete(storedFilename);

        // Then
        assertThat(Files.exists(storageLocation.resolve(storedFilename))).isFalse();
    }

    @Test
    void shouldDeleteThumbnail() throws IOException {
        // Given
        String thumbnailFilename = "thumb_test.jpg";
        Files.createFile(thumbnailsLocation.resolve(thumbnailFilename));
        assertThat(Files.exists(thumbnailsLocation.resolve(thumbnailFilename))).isTrue();

        // When
        fileStorageService.deleteThumbnail(thumbnailFilename);

        // Then
        assertThat(Files.exists(thumbnailsLocation.resolve(thumbnailFilename))).isFalse();
    }

    @Test
    void shouldRejectEmptyFile() {
        // Given
        MultipartFile emptyFile = new MockMultipartFile(
                "empty.jpg",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        // When/Then
        assertThatThrownBy(() -> fileStorageService.store(emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to store empty file");
    }

    @Test
    void shouldRejectFileWithInvalidPath() {
        // Given
        MultipartFile file = new MockMultipartFile(
                "../../../etc/passwd",
                "../../../etc/passwd",
                "text/plain",
                "malicious content".getBytes()
        );

        // When/Then
        assertThatThrownBy(() -> fileStorageService.store(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cannot store file outside");
    }

    @Test
    void shouldGetFilePath() throws IOException {
        // Given
        MultipartFile file = new MockMultipartFile(
                "test.jpg",
                "test.jpg",
                "image/jpeg",
                "test content".getBytes()
        );
        String storedFilename = fileStorageService.store(file);

        // When
        Path filePath = fileStorageService.getFilePath(storedFilename);

        // Then
        assertThat(filePath).isNotNull();
        assertThat(Files.exists(filePath)).isTrue();
        assertThat(filePath.getParent()).isEqualTo(storageLocation);
    }

    @Test
    void shouldGetThumbnailPath() {
        // Given
        String thumbnailFilename = "thumb_test.jpg";

        // When
        Path thumbnailPath = fileStorageService.getThumbnailPath(thumbnailFilename);

        // Then
        assertThat(thumbnailPath).isNotNull();
        assertThat(thumbnailPath.getParent()).isEqualTo(thumbnailsLocation);
        assertThat(thumbnailPath.getFileName().toString()).isEqualTo(thumbnailFilename);
    }
}
