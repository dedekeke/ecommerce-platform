package com.ecommerce.mediaservice.controller;

import com.ecommerce.mediaservice.config.SecurityConfig;
import com.ecommerce.mediaservice.dto.MediaResponse;
import com.ecommerce.mediaservice.exception.GlobalExceptionHandler;
import com.ecommerce.mediaservice.exception.MediaNotFoundException;
import com.ecommerce.mediaservice.service.MediaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(MediaController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "security.enabled=true",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.test/"
})
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MediaService mediaService;

    // Satisfies the oauth2 resource-server filter chain without hitting a real issuer.
    @MockBean
    private JwtDecoder jwtDecoder;

    private static final String USER_ID = "auth0|123456";
    private static final String OTHER_USER_ID = "auth0|999999";
    private static final String MEDIA_ID = "media-123";

    @Test
    void shouldUploadFile() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-image.jpg",
                "image/jpeg",
                "test content".getBytes()
        );

        MediaResponse response = MediaResponse.builder()
                .id(MEDIA_ID)
                .filename("test-image.jpg")
                .contentType("image/jpeg")
                .size(12L)
                .downloadUrl("/api/media/" + MEDIA_ID + "/download")
                .uploadedBy(USER_ID)
                .build();

        when(mediaService.uploadFile(any(), anyString())).thenReturn(response);

        // When/Then
        mockMvc.perform(multipart("/api/media/upload")
                        .file(file)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(MEDIA_ID))
                .andExpect(jsonPath("$.filename").value("test-image.jpg"))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"));

        verify(mediaService).uploadFile(any(), eq(USER_ID));
    }

    @Test
    void shouldReturn401WhenUploadingUnauthenticated() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        // When/Then - no bearer token -> oauth2 resource server rejects with 401
        mockMvc.perform(multipart("/api/media/upload").file(file))
                .andExpect(status().isUnauthorized());

        verify(mediaService, never()).uploadFile(any(), any());
    }

    @Test
    void shouldGetOwnMediaById() throws Exception {
        // Given
        MediaResponse response = MediaResponse.builder()
                .id(MEDIA_ID)
                .filename("test.jpg")
                .contentType("image/jpeg")
                .size(1024L)
                .downloadUrl("/api/media/" + MEDIA_ID + "/download")
                .uploadedBy(USER_ID)
                .build();

        when(mediaService.getMediaById(MEDIA_ID, USER_ID, false)).thenReturn(response);

        // When/Then
        mockMvc.perform(get("/api/media/{id}", MEDIA_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(MEDIA_ID))
                .andExpect(jsonPath("$.filename").value("test.jpg"));

        verify(mediaService).getMediaById(MEDIA_ID, USER_ID, false);
    }

    @Test
    void shouldReturn404WhenMediaNotFound() throws Exception {
        // Given
        when(mediaService.getMediaById(MEDIA_ID, USER_ID, false))
                .thenThrow(new MediaNotFoundException(MEDIA_ID));

        // When/Then
        mockMvc.perform(get("/api/media/{id}", MEDIA_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", USER_ID))))
                .andExpect(status().isNotFound());

        verify(mediaService).getMediaById(MEDIA_ID, USER_ID, false);
    }

    @Test
    void shouldReturn404WhenGettingOtherUsersMedia() throws Exception {
        // Given - service reports non-owned media as not found (enumeration hardening)
        when(mediaService.getMediaById(MEDIA_ID, OTHER_USER_ID, false))
                .thenThrow(new MediaNotFoundException(MEDIA_ID));

        // When/Then
        mockMvc.perform(get("/api/media/{id}", MEDIA_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", OTHER_USER_ID))))
                .andExpect(status().isNotFound());

        verify(mediaService).getMediaById(MEDIA_ID, OTHER_USER_ID, false);
    }

    @Test
    void shouldReturn401WhenGettingMediaUnauthenticated() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/media/{id}", MEDIA_ID))
                .andExpect(status().isUnauthorized());

        verify(mediaService, never()).getMediaById(anyString(), anyString(), anyBoolean());
    }

    @Test
    void shouldAllowAdminToGetAnyMedia() throws Exception {
        // Given
        MediaResponse response = MediaResponse.builder()
                .id(MEDIA_ID)
                .filename("test.jpg")
                .contentType("image/jpeg")
                .uploadedBy(USER_ID)
                .build();

        when(mediaService.getMediaById(MEDIA_ID, OTHER_USER_ID, true)).thenReturn(response);

        // When/Then - caller holds SCOPE_admin, media owned by someone else
        mockMvc.perform(get("/api/media/{id}", MEDIA_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", OTHER_USER_ID))
                                .authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(MEDIA_ID));

        verify(mediaService).getMediaById(MEDIA_ID, OTHER_USER_ID, true);
    }

    @Test
    void shouldDownloadOwnFile() throws Exception {
        // Given
        byte[] fileContent = "test file content".getBytes();
        Resource resource = new ByteArrayResource(fileContent);

        MediaResponse mediaResponse = MediaResponse.builder()
                .id(MEDIA_ID)
                .filename("test.jpg")
                .contentType("image/jpeg")
                .build();

        when(mediaService.getMediaById(MEDIA_ID, USER_ID, false)).thenReturn(mediaResponse);
        when(mediaService.loadMediaFile(MEDIA_ID, USER_ID, false)).thenReturn(resource);

        // When/Then
        mockMvc.perform(get("/api/media/{id}/download", MEDIA_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"test.jpg\""))
                .andExpect(content().bytes(fileContent));

        verify(mediaService).getMediaById(MEDIA_ID, USER_ID, false);
        verify(mediaService).loadMediaFile(MEDIA_ID, USER_ID, false);
    }

    @Test
    void shouldReturn404WhenDownloadingOtherUsersFile() throws Exception {
        // Given
        when(mediaService.getMediaById(MEDIA_ID, OTHER_USER_ID, false))
                .thenThrow(new MediaNotFoundException(MEDIA_ID));

        // When/Then
        mockMvc.perform(get("/api/media/{id}/download", MEDIA_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", OTHER_USER_ID))))
                .andExpect(status().isNotFound());

        verify(mediaService).getMediaById(MEDIA_ID, OTHER_USER_ID, false);
        verify(mediaService, never()).loadMediaFile(anyString(), anyString(), anyBoolean());
    }

    @Test
    void shouldReturn401WhenDownloadingUnauthenticated() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/media/{id}/download", MEDIA_ID))
                .andExpect(status().isUnauthorized());

        verify(mediaService, never()).loadMediaFile(anyString(), anyString(), anyBoolean());
    }

    @Test
    void shouldDeleteOwnMedia() throws Exception {
        // Given
        doNothing().when(mediaService).deleteMedia(eq(MEDIA_ID), eq(USER_ID), eq(false));

        // When/Then
        mockMvc.perform(delete("/api/media/{id}", MEDIA_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", USER_ID))))
                .andExpect(status().isNoContent());

        verify(mediaService).deleteMedia(MEDIA_ID, USER_ID, false);
    }

    @Test
    void shouldReturn403WhenDeletingOthersMedia() throws Exception {
        // Given
        doThrow(new SecurityException("Not authorized"))
                .when(mediaService).deleteMedia(eq(MEDIA_ID), eq(USER_ID), eq(false));

        // When/Then
        mockMvc.perform(delete("/api/media/{id}", MEDIA_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", USER_ID))))
                .andExpect(status().isForbidden());

        verify(mediaService).deleteMedia(MEDIA_ID, USER_ID, false);
    }

    @Test
    void shouldAllowAdminToDeleteAnyMedia() throws Exception {
        // Given
        doNothing().when(mediaService).deleteMedia(eq(MEDIA_ID), eq(OTHER_USER_ID), eq(true));

        // When/Then
        mockMvc.perform(delete("/api/media/{id}", MEDIA_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", OTHER_USER_ID))
                                .authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
                .andExpect(status().isNoContent());

        verify(mediaService).deleteMedia(MEDIA_ID, OTHER_USER_ID, true);
    }

    @Test
    void shouldGetOwnUserMedia() throws Exception {
        // Given
        List<MediaResponse> mediaList = Arrays.asList(
                MediaResponse.builder()
                        .id("media-1")
                        .filename("file1.jpg")
                        .contentType("image/jpeg")
                        .uploadedBy(USER_ID)
                        .build(),
                MediaResponse.builder()
                        .id("media-2")
                        .filename("file2.png")
                        .contentType("image/png")
                        .uploadedBy(USER_ID)
                        .build()
        );

        when(mediaService.getMediaByUser(USER_ID, USER_ID, false)).thenReturn(mediaList);

        // When/Then
        mockMvc.perform(get("/api/media/user/{userId}", USER_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("media-1"))
                .andExpect(jsonPath("$[1].id").value("media-2"));

        verify(mediaService).getMediaByUser(USER_ID, USER_ID, false);
    }

    @Test
    void shouldReturn403WhenListingOtherUsersMedia() throws Exception {
        // Given - listing another user's uploads is a 403 authorization failure
        when(mediaService.getMediaByUser(eq(USER_ID), eq(OTHER_USER_ID), eq(false)))
                .thenThrow(new SecurityException("Not authorized"));

        // When/Then
        mockMvc.perform(get("/api/media/user/{userId}", USER_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", OTHER_USER_ID))))
                .andExpect(status().isForbidden());

        verify(mediaService).getMediaByUser(USER_ID, OTHER_USER_ID, false);
    }

    @Test
    void shouldAllowAdminToListAnyUsersMedia() throws Exception {
        // Given
        List<MediaResponse> mediaList = Arrays.asList(
                MediaResponse.builder().id("media-1").filename("file1.jpg").uploadedBy(USER_ID).build()
        );

        when(mediaService.getMediaByUser(USER_ID, OTHER_USER_ID, true)).thenReturn(mediaList);

        // When/Then
        mockMvc.perform(get("/api/media/user/{userId}", USER_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", OTHER_USER_ID))
                                .authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(mediaService).getMediaByUser(USER_ID, OTHER_USER_ID, true);
    }

    @Test
    void shouldReturn401WhenListingUnauthenticated() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/media/user/{userId}", USER_ID))
                .andExpect(status().isUnauthorized());

        verify(mediaService, never()).getMediaByUser(anyString(), anyString(), anyBoolean());
    }

    @Test
    void shouldReturn400WhenUploadingEmptyFile() throws Exception {
        // Given
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        when(mediaService.uploadFile(any(), anyString()))
                .thenThrow(new IllegalArgumentException("Cannot upload empty file"));

        // When/Then
        mockMvc.perform(multipart("/api/media/upload")
                        .file(emptyFile)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", USER_ID))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenFileTypeMismatch() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "virus.exe",
                "application/x-msdownload",
                "malicious".getBytes()
        );

        when(mediaService.uploadFile(any(), anyString()))
                .thenThrow(new IllegalArgumentException("File type not allowed"));

        // When/Then
        mockMvc.perform(multipart("/api/media/upload")
                        .file(file)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", USER_ID))))
                .andExpect(status().isBadRequest());
    }
}
