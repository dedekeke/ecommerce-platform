package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.domain.UserRole;
import com.ecommerce.userservice.dto.UpdateProfileRequest;
import com.ecommerce.userservice.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for User Controller
 *
 * Uses Testcontainers to spin up a real PostgreSQL database for testing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class UserControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine")
            .withDatabaseName("userdb_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // Disable Eureka for tests
        registry.add("eureka.client.enabled", () -> "false");

        // Disable Auth0 validation for tests (we'll mock JWT)
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String auth0Id;
    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        auth0Id = "auth0|test123";
        testUser = User.builder()
                .auth0Id(auth0Id)
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .role(UserRole.USER)
                .active(true)
                .emailVerified(true)
                .lastLoginAt(Instant.now())
                .build();

        testUser = userRepository.save(testUser);
    }

    @Test
    void testGetCurrentUserProfile_existingUser_shouldReturnProfile() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .with(jwt()
                                .jwt(jwt -> jwt.subject(auth0Id))
                                .authorities(new SimpleGrantedAuthority("SCOPE_read:profile"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // UserProfileResponse exposes the public id (auth0Id is internal
                // and intentionally not serialized).
                .andExpect(jsonPath("$.id", is(testUser.getId().toString())))
                .andExpect(jsonPath("$.email", is("test@example.com")))
                .andExpect(jsonPath("$.firstName", is("John")))
                .andExpect(jsonPath("$.lastName", is("Doe")))
                .andExpect(jsonPath("$.role", is("USER")));
    }

    @Test
    void testGetCurrentUserProfile_newUser_shouldCreateAndReturnProfile() throws Exception {
        String newAuth0Id = "auth0|newuser456";

        mockMvc.perform(get("/api/users/me")
                        .with(jwt()
                                .jwt(jwt -> jwt
                                        .subject(newAuth0Id)
                                        .claim("email", "newuser@example.com")
                                        .claim("given_name", "Jane")
                                        .claim("family_name", "Smith")
                                        .claim("email_verified", true))
                                .authorities(new SimpleGrantedAuthority("SCOPE_read:profile"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // auth0Id is internal and not serialized; assert returned fields.
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.email", is("newuser@example.com")))
                .andExpect(jsonPath("$.firstName", is("Jane")))
                .andExpect(jsonPath("$.lastName", is("Smith")));

        // Verify user was created in database
        User createdUser = userRepository.findByAuth0Id(newAuth0Id).orElse(null);
        assertThat(createdUser).isNotNull();
        assertThat(createdUser.getEmail()).isEqualTo("newuser@example.com");
    }

    @Test
    void testUpdateCurrentUserProfile_validData_shouldUpdateProfile() throws Exception {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .phoneNumber("+12125551234")
                .build();

        mockMvc.perform(put("/api/users/me")
                        .with(jwt()
                                .jwt(jwt -> jwt.subject(auth0Id))
                                .authorities(new SimpleGrantedAuthority("SCOPE_write:profile")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.firstName", is("Jane")))
                .andExpect(jsonPath("$.lastName", is("Smith")))
                .andExpect(jsonPath("$.phoneNumber", is("+12125551234")));

        // Verify in database
        User updatedUser = userRepository.findByAuth0Id(auth0Id).orElse(null);
        assertThat(updatedUser).isNotNull();
        assertThat(updatedUser.getFirstName()).isEqualTo("Jane");
        assertThat(updatedUser.getLastName()).isEqualTo("Smith");
        assertThat(updatedUser.getPhoneNumber()).isEqualTo("+12125551234");
    }

    @Test
    void testUpdateCurrentUserProfile_invalidPhoneNumber_shouldReturnBadRequest() throws Exception {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .phoneNumber("invalid-phone")
                .build();

        mockMvc.perform(put("/api/users/me")
                        .with(jwt()
                                .jwt(jwt -> jwt.subject(auth0Id))
                                .authorities(new SimpleGrantedAuthority("SCOPE_write:profile")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.validationErrors.phoneNumber", notNullValue()));
    }

    @Test
    void testUpdateCurrentUserProfile_tooLongFirstName_shouldReturnBadRequest() throws Exception {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .firstName("a".repeat(101)) // Exceeds 100 character limit
                .lastName("Smith")
                .build();

        mockMvc.perform(put("/api/users/me")
                        .with(jwt()
                                .jwt(jwt -> jwt.subject(auth0Id))
                                .authorities(new SimpleGrantedAuthority("SCOPE_write:profile")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.validationErrors.firstName", notNullValue()));
    }

    @Test
    void testGetUserById_existingUser_shouldReturnProfile() throws Exception {
        mockMvc.perform(get("/api/users/" + testUser.getId())
                        .with(jwt()
                                .jwt(jwt -> jwt.subject("auth0|admin"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_read:users"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // id is serialized as a String in UserProfileResponse.
                .andExpect(jsonPath("$.id", is(testUser.getId().toString())))
                .andExpect(jsonPath("$.email", is("test@example.com")));
    }

    @Test
    void testGetUserById_nonExistentUser_shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/users/99999")
                        .with(jwt()
                                .jwt(jwt -> jwt.subject("auth0|admin"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_read:users"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetUserByAuth0Id_adminScope_shouldReturnMinimalContact() throws Exception {
        mockMvc.perform(get("/api/users/by-auth0/{sub}", auth0Id)
                        .with(jwt()
                                .jwt(jwt -> jwt.subject("auth0|caller"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.email", is("test@example.com")))
                .andExpect(jsonPath("$.fullName", is("John Doe")))
                // PII fields must NOT leak through this internal endpoint
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.active").doesNotExist())
                .andExpect(jsonPath("$.emailVerified").doesNotExist());
    }

    @Test
    void testGetUserByAuth0Id_internalServiceScope_shouldReturnContact() throws Exception {
        mockMvc.perform(get("/api/users/by-auth0/{sub}", auth0Id)
                        .with(jwt()
                                .jwt(jwt -> jwt.subject("auth0|caller"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_internal:service"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("test@example.com")))
                .andExpect(jsonPath("$.fullName", is("John Doe")));
    }

    @Test
    void testGetUserByAuth0Id_insufficientScope_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/users/by-auth0/{sub}", auth0Id)
                        .with(jwt()
                                .jwt(jwt -> jwt.subject("auth0|caller"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_read:profile"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetUserByAuth0Id_nonExistentUser_shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/users/by-auth0/{sub}", "auth0|ghost")
                        .with(jwt()
                                .jwt(jwt -> jwt.subject("auth0|caller"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
                .andExpect(status().isNotFound());
    }

}
