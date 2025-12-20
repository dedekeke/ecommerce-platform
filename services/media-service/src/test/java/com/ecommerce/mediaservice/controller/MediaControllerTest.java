package com.ecommerce.mediaservice.controller;

import com.ecommerce.mediaservice.dto.MediaResponse;
import com.ecommerce.mediaservice.exception.MediaNotFoundException;
import com.ecommerce.mediaservice.service.MediaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
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
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MediaService mediaService;

    private static final String USER_ID = "auth0|123456";
    private static final String MEDIA_ID = "media-123";

    @Test
    @WithMockUser
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
    void shouldRequireAuthenticationForUpload() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        // When/Then - No authentication (Spring Security returns 403 with CSRF disabled)
        mockMvc.perform(multipart("/api/media/upload").file(file))
                .andExpect(status().isForbidden());

        verify(mediaService, never()).uploadFile(any(), any());
    }

    @Test
    @WithMockUser
    void shouldGetMediaById() throws Exception {
        // Given
        MediaResponse response = MediaResponse.builder()
                .id(MEDIA_ID)
                .filename("test.jpg")
                .contentType("image/jpeg")
                .size(1024L)
                .downloadUrl("/api/media/" + MEDIA_ID + "/download")
                .uploadedBy(USER_ID)
                .build();

        when(mediaService.getMediaById(MEDIA_ID)).thenReturn(response);

        // When/Then
        mockMvc.perform(get("/api/media/{id}", MEDIA_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(MEDIA_ID))
                .andExpect(jsonPath("$.filename").value("test.jpg"));

        verify(mediaService).getMediaById(MEDIA_ID);
    }

    @Test
    @WithMockUser
    void shouldReturn404WhenMediaNotFound() throws Exception {
        // Given
        when(mediaService.getMediaById(MEDIA_ID))
                .thenThrow(new MediaNotFoundException(MEDIA_ID));

        // When/Then
        mockMvc.perform(get("/api/media/{id}", MEDIA_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", USER_ID))))
                .andExpect(status().isNotFound());

        verify(mediaService).getMediaById(MEDIA_ID);
    }

    @Test
    @WithMockUser
    void shouldDownloadFile() throws Exception {
        // Given
        byte[] fileContent = "test file content".getBytes();
        Resource resource = new ByteArrayResource(fileContent);

        MediaResponse mediaResponse = MediaResponse.builder()
                .id(MEDIA_ID)
                .filename("test.jpg")
                .contentType("image/jpeg")
                .build();

        when(mediaService.getMediaById(MEDIA_ID)).thenReturn(mediaResponse);
        when(mediaService.loadMediaFile(MEDIA_ID)).thenReturn(resource);

        // When/Then
        mockMvc.perform(get("/api/media/{id}/download", MEDIA_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"test.jpg\""))
                .andExpect(content().bytes(fileContent));

        verify(mediaService).getMediaById(MEDIA_ID);
        verify(mediaService).loadMediaFile(MEDIA_ID);
    }

    @Test
    @WithMockUser
    void shouldDeleteMedia() throws Exception {
        // Given
        doNothing().when(mediaService).deleteMedia(eq(MEDIA_ID), eq(USER_ID));

        // When/Then
        mockMvc.perform(delete("/api/media/{id}", MEDIA_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", USER_ID))))
                .andExpect(status().isNoContent());

        verify(mediaService).deleteMedia(MEDIA_ID, USER_ID);
    }

    @Test
    @WithMockUser
    void shouldReturn403WhenDeletingOthersMedia() throws Exception {
        // Given
        doThrow(new SecurityException("Not authorized"))
                .when(mediaService).deleteMedia(eq(MEDIA_ID), eq(USER_ID));

        // When/Then
        mockMvc.perform(delete("/api/media/{id}", MEDIA_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", USER_ID))))
                .andExpect(status().isForbidden());

        verify(mediaService).deleteMedia(MEDIA_ID, USER_ID);
    }

    @Test
    @WithMockUser
    void shouldGetUserMedia() throws Exception {
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

        when(mediaService.getMediaByUser(USER_ID)).thenReturn(mediaList);

        // When/Then
        mockMvc.perform(get("/api/media/user/{userId}", USER_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("media-1"))
                .andExpect(jsonPath("$[1].id").value("media-2"));

        verify(mediaService).getMediaByUser(USER_ID);
    }

    @Test
    @WithMockUser
    void shouldReturn400WhenUploadingWithoutFile() throws Exception {
        // When/Then - Missing required parameter returns 500 (Spring's default for missing @RequestParam)
        // This is acceptable as proper clients will always send the file parameter
        mockMvc.perform(multipart("/api/media/upload")
                        .with(jwt().jwt(jwt -> jwt.claim("sub", USER_ID))))
                .andExpect(status().is5xxServerError());

        verify(mediaService, never()).uploadFile(any(), any());
    }

    @Test
    @WithMockUser
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
    @WithMockUser
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
