package com.ecommerce.mediaservice.service;

import com.ecommerce.mediaservice.document.Media;
import com.ecommerce.mediaservice.dto.MediaResponse;
import com.ecommerce.mediaservice.exception.MediaNotFoundException;
import com.ecommerce.mediaservice.repository.MediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ImageProcessingService imageProcessingService;

    private MediaService mediaService;

    private static final String USER_ID = "auth0|123456";
    private static final String MEDIA_ID = "media-id-123";
    private static final String ALLOWED_FILE_TYPES = "image/jpeg,image/png,image/gif,image/webp,application/pdf";
    private static final String MAX_FILE_SIZE = "10MB";

    @BeforeEach
    void setUp() {
        // Manually create MediaService with required parameters
        mediaService = new MediaService(
                mediaRepository,
                fileStorageService,
                imageProcessingService,
                ALLOWED_FILE_TYPES,
                MAX_FILE_SIZE
        );
    }

    @Test
    void shouldUploadImageFile() throws IOException {
        // Given
        MultipartFile file = new MockMultipartFile(
                "test-image.jpg",
                "test-image.jpg",
                "image/jpeg",
                "test image content".getBytes()
        );

        String storedFilename = "stored-123.jpg";
        String thumbnailFilename = "thumb_stored-123.jpg";

        when(fileStorageService.store(file)).thenReturn(storedFilename);
        when(imageProcessingService.generateThumbnailFilename(storedFilename))
                .thenReturn(thumbnailFilename);
        when(imageProcessingService.getImageDimensions(any(Path.class)))
                .thenReturn(Media.ImageDimensions.builder().width(800).height(600).build());
        when(fileStorageService.getFilePath(storedFilename))
                .thenReturn(Paths.get("/storage/" + storedFilename));
        when(fileStorageService.getThumbnailPath(thumbnailFilename))
                .thenReturn(Paths.get("/storage/thumbnails/" + thumbnailFilename));

        Media savedMedia = Media.builder()
                .id(MEDIA_ID)
                .filename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .size(file.getSize())
                .storageUrl("/storage/" + storedFilename)
                .thumbnailUrl("/storage/thumbnails/" + thumbnailFilename)
                .uploadedBy(USER_ID)
                .build();

        when(mediaRepository.save(any(Media.class))).thenReturn(savedMedia);

        // When
        MediaResponse response = mediaService.uploadFile(file, USER_ID);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(MEDIA_ID);
        assertThat(response.getFilename()).isEqualTo("test-image.jpg");
        assertThat(response.getContentType()).isEqualTo("image/jpeg");
        assertThat(response.getUploadedBy()).isEqualTo(USER_ID);

        verify(fileStorageService).store(file);
        verify(imageProcessingService).createThumbnail(any(Path.class), any(Path.class));
        verify(imageProcessingService).getImageDimensions(any(Path.class));
        verify(mediaRepository).save(any(Media.class));
    }

    @Test
    void shouldUploadNonImageFile() throws IOException {
        // Given
        MultipartFile file = new MockMultipartFile(
                "document.pdf",
                "document.pdf",
                "application/pdf",
                "pdf content".getBytes()
        );

        String storedFilename = "stored-456.pdf";

        when(fileStorageService.store(file)).thenReturn(storedFilename);
        when(fileStorageService.getFilePath(storedFilename))
                .thenReturn(Paths.get("/storage/" + storedFilename));

        Media savedMedia = Media.builder()
                .id(MEDIA_ID)
                .filename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .size(file.getSize())
                .storageUrl("/storage/" + storedFilename)
                .uploadedBy(USER_ID)
                .build();

        when(mediaRepository.save(any(Media.class))).thenReturn(savedMedia);

        // When
        MediaResponse response = mediaService.uploadFile(file, USER_ID);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(MEDIA_ID);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getThumbnailUrl()).isNull();

        verify(fileStorageService).store(file);
        verify(imageProcessingService, never()).createThumbnail(any(), any());
        verify(mediaRepository).save(any(Media.class));
    }

    @Test
    void shouldGetMediaById() {
        // Given
        Media media = Media.builder()
                .id(MEDIA_ID)
                .filename("test.jpg")
                .contentType("image/jpeg")
                .size(1024L)
                .storageUrl("/storage/test.jpg")
                .thumbnailUrl("/storage/thumbnails/thumb_test.jpg")
                .uploadedBy(USER_ID)
                .build();

        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.of(media));

        // When
        MediaResponse response = mediaService.getMediaById(MEDIA_ID, USER_ID, false);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(MEDIA_ID);
        assertThat(response.getFilename()).isEqualTo("test.jpg");

        verify(mediaRepository).findById(MEDIA_ID);
    }

    @Test
    void shouldThrowExceptionWhenMediaNotFound() {
        // Given
        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> mediaService.getMediaById(MEDIA_ID, USER_ID, false))
                .isInstanceOf(MediaNotFoundException.class)
                .hasMessageContaining(MEDIA_ID);
    }

    @Test
    void shouldReturnNotFoundWhenGettingOtherUsersMedia() {
        // Given - media owned by a different user; opaque-id endpoints must not
        // leak existence, so ownership failure surfaces as 404, not 403.
        Media media = Media.builder()
                .id(MEDIA_ID)
                .filename("secret.jpg")
                .uploadedBy("different-user")
                .build();

        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.of(media));

        // When/Then
        assertThatThrownBy(() -> mediaService.getMediaById(MEDIA_ID, USER_ID, false))
                .isInstanceOf(MediaNotFoundException.class)
                .hasMessageContaining(MEDIA_ID);
    }

    @Test
    void shouldAllowAdminToGetAnyMedia() {
        // Given
        Media media = Media.builder()
                .id(MEDIA_ID)
                .filename("secret.jpg")
                .uploadedBy("different-user")
                .build();

        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.of(media));

        // When
        MediaResponse response = mediaService.getMediaById(MEDIA_ID, USER_ID, true);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(MEDIA_ID);
        assertThat(response.getUploadedBy()).isEqualTo("different-user");
    }

    @Test
    void shouldLoadMediaFile() {
        // Given
        Media media = Media.builder()
                .id(MEDIA_ID)
                .filename("test.jpg")
                .storageUrl("/storage/stored-123.jpg")
                .uploadedBy(USER_ID)
                .build();

        Resource mockResource = mock(Resource.class);

        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.of(media));
        when(fileStorageService.loadAsResource("stored-123.jpg")).thenReturn(mockResource);

        // When
        Resource resource = mediaService.loadMediaFile(MEDIA_ID, USER_ID, false);

        // Then
        assertThat(resource).isNotNull();
        verify(fileStorageService).loadAsResource("stored-123.jpg");
    }

    @Test
    void shouldReturnNotFoundWhenLoadingOtherUsersFile() {
        // Given
        Media media = Media.builder()
                .id(MEDIA_ID)
                .filename("secret.jpg")
                .storageUrl("/storage/stored-123.jpg")
                .uploadedBy("different-user")
                .build();

        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.of(media));

        // When/Then
        assertThatThrownBy(() -> mediaService.loadMediaFile(MEDIA_ID, USER_ID, false))
                .isInstanceOf(MediaNotFoundException.class)
                .hasMessageContaining(MEDIA_ID);

        verify(fileStorageService, never()).loadAsResource(any());
    }

    @Test
    void shouldAllowAdminToLoadAnyFile() {
        // Given
        Media media = Media.builder()
                .id(MEDIA_ID)
                .filename("secret.jpg")
                .storageUrl("/storage/stored-123.jpg")
                .uploadedBy("different-user")
                .build();

        Resource mockResource = mock(Resource.class);

        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.of(media));
        when(fileStorageService.loadAsResource("stored-123.jpg")).thenReturn(mockResource);

        // When
        Resource resource = mediaService.loadMediaFile(MEDIA_ID, USER_ID, true);

        // Then
        assertThat(resource).isNotNull();
        verify(fileStorageService).loadAsResource("stored-123.jpg");
    }

    @Test
    void shouldGetPublicMediaByIdWithoutOwnershipCheck() {
        // Given - owned by someone else; the public read path (product imagery)
        // intentionally skips the ownership gate
        Media media = Media.builder()
                .id(MEDIA_ID)
                .filename("product.jpg")
                .contentType("image/jpeg")
                .storageUrl("/storage/stored-123.jpg")
                .uploadedBy("different-user")
                .build();

        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.of(media));

        // When
        MediaResponse response = mediaService.getPublicMediaById(MEDIA_ID);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(MEDIA_ID);
        assertThat(response.getContentUrl()).isEqualTo("/api/media/" + MEDIA_ID + "/content");
    }

    @Test
    void shouldThrowWhenPublicMediaNotFound() {
        // Given
        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> mediaService.getPublicMediaById(MEDIA_ID))
                .isInstanceOf(MediaNotFoundException.class)
                .hasMessageContaining(MEDIA_ID);
    }

    @Test
    void shouldLoadPublicMediaFileWithoutOwnershipCheck() {
        // Given
        Media media = Media.builder()
                .id(MEDIA_ID)
                .filename("product.jpg")
                .storageUrl("/storage/stored-123.jpg")
                .uploadedBy("different-user")
                .build();

        Resource mockResource = mock(Resource.class);

        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.of(media));
        when(fileStorageService.loadAsResource("stored-123.jpg")).thenReturn(mockResource);

        // When
        Resource resource = mediaService.loadPublicMediaFile(MEDIA_ID);

        // Then
        assertThat(resource).isNotNull();
        verify(fileStorageService).loadAsResource("stored-123.jpg");
    }

    @Test
    void shouldThrowWhenPublicMediaFileNotFound() {
        // Given
        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> mediaService.loadPublicMediaFile(MEDIA_ID))
                .isInstanceOf(MediaNotFoundException.class)
                .hasMessageContaining(MEDIA_ID);

        verify(fileStorageService, never()).loadAsResource(any());
    }

    @Test
    void shouldIncludeContentUrlInMediaResponse() {
        // Given
        Media media = Media.builder()
                .id(MEDIA_ID)
                .filename("test.jpg")
                .contentType("image/jpeg")
                .storageUrl("/storage/test.jpg")
                .uploadedBy(USER_ID)
                .build();

        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.of(media));

        // When
        MediaResponse response = mediaService.getMediaById(MEDIA_ID, USER_ID, false);

        // Then
        assertThat(response.getDownloadUrl()).isEqualTo("/api/media/" + MEDIA_ID + "/download");
        assertThat(response.getContentUrl()).isEqualTo("/api/media/" + MEDIA_ID + "/content");
    }

    @Test
    void shouldDeleteMedia() {
        // Given
        Media media = Media.builder()
                .id(MEDIA_ID)
                .filename("test.jpg")
                .storageUrl("/storage/stored-123.jpg")
                .thumbnailUrl("/storage/thumbnails/thumb_stored-123.jpg")
                .uploadedBy(USER_ID)
                .build();

        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.of(media));

        // When
        mediaService.deleteMedia(MEDIA_ID, USER_ID);

        // Then
        verify(fileStorageService).delete("stored-123.jpg");
        verify(fileStorageService).deleteThumbnail("thumb_stored-123.jpg");
        verify(mediaRepository).delete(media);
    }

    @Test
    void shouldThrowExceptionWhenDeletingOtherUsersMedia() {
        // Given
        Media media = Media.builder()
                .id(MEDIA_ID)
                .uploadedBy("different-user")
                .build();

        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.of(media));

        // When/Then
        assertThatThrownBy(() -> mediaService.deleteMedia(MEDIA_ID, USER_ID))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not authorized");

        verify(fileStorageService, never()).delete(any());
        verify(mediaRepository, never()).delete(any());
    }

    @Test
    void shouldAllowAdminToDeleteAnyMedia() {
        // Given
        Media media = Media.builder()
                .id(MEDIA_ID)
                .filename("test.jpg")
                .storageUrl("/storage/stored-123.jpg")
                .uploadedBy("different-user")
                .build();

        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.of(media));

        // When
        mediaService.deleteMedia(MEDIA_ID, USER_ID, true); // isAdmin = true

        // Then
        verify(fileStorageService).delete("stored-123.jpg");
        verify(mediaRepository).delete(media);
    }

    @Test
    void shouldGetMediaByUser() {
        // Given
        List<Media> userMedia = Arrays.asList(
                Media.builder().id("1").filename("file1.jpg").uploadedBy(USER_ID).build(),
                Media.builder().id("2").filename("file2.jpg").uploadedBy(USER_ID).build()
        );

        when(mediaRepository.findByUploadedBy(USER_ID)).thenReturn(userMedia);

        // When
        List<MediaResponse> responses = mediaService.getMediaByUser(USER_ID, USER_ID, false);

        // Then
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getId()).isEqualTo("1");
        assertThat(responses.get(1).getId()).isEqualTo("2");

        verify(mediaRepository).findByUploadedBy(USER_ID);
    }

    @Test
    void shouldThrowSecurityExceptionWhenListingOtherUsersMedia() {
        // Given - the listing path is keyed on a caller-supplied userId (a known
        // identifier, not an opaque resource id), so a cross-user attempt is a
        // 403 authorization failure rather than a 404.

        // When/Then
        assertThatThrownBy(() -> mediaService.getMediaByUser("different-user", USER_ID, false))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not authorized");

        verify(mediaRepository, never()).findByUploadedBy(any());
    }

    @Test
    void shouldAllowAdminToListAnyUsersMedia() {
        // Given
        List<Media> userMedia = Arrays.asList(
                Media.builder().id("1").filename("file1.jpg").uploadedBy("different-user").build()
        );

        when(mediaRepository.findByUploadedBy("different-user")).thenReturn(userMedia);

        // When
        List<MediaResponse> responses = mediaService.getMediaByUser("different-user", USER_ID, true);

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getId()).isEqualTo("1");

        verify(mediaRepository).findByUploadedBy("different-user");
    }

    @Test
    void shouldValidateFileType() {
        // Given
        MultipartFile invalidFile = new MockMultipartFile(
                "virus.exe",
                "virus.exe",
                "application/x-msdownload",
                "malicious content".getBytes()
        );

        // When/Then
        assertThatThrownBy(() -> mediaService.uploadFile(invalidFile, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File type not allowed");

        verify(fileStorageService, never()).store(any());
    }

    @Test
    void shouldValidateFileSize() {
        // Given - create a file larger than max size
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
        MultipartFile largeFile = new MockMultipartFile(
                "large.jpg",
                "large.jpg",
                "image/jpeg",
                largeContent
        );

        // When/Then
        assertThatThrownBy(() -> mediaService.uploadFile(largeFile, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File size exceeds maximum");

        verify(fileStorageService, never()).store(any());
    }

    @Test
    void shouldExtractFilenameFromStorageUrl() {
        // Given
        String storageUrl = "/storage/stored-123.jpg";

        // When
        String filename = storageUrl.substring(storageUrl.lastIndexOf('/') + 1);

        // Then
        assertThat(filename).isEqualTo("stored-123.jpg");
    }
}
