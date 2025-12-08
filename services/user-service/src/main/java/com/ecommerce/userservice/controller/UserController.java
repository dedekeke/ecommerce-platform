package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.dto.UpdateProfileRequest;
import com.ecommerce.userservice.dto.UserProfileResponse;
import com.ecommerce.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * User Controller
 *
 * REST API endpoints for user profile management.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User", description = "User profile management endpoints")
@SecurityRequirement(name = "bearer-jwt")
public class UserController {

    private final UserService userService;

    /**
     * Get current user's profile
     *
     * GET /api/users/me
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user profile", description = "Get the authenticated user's profile information")
    public ResponseEntity<UserProfileResponse> getCurrentUserProfile(@AuthenticationPrincipal Jwt jwt) {
        String auth0Id = jwt.getSubject();
        Map<String, Object> claims = jwt.getClaims();

        log.debug("Getting profile for user: {}", auth0Id);

        // Get or create user from Auth0 (handles first login sync)
        User user = userService.getOrCreateFromAuth0(auth0Id, claims);

        return ResponseEntity.ok(UserProfileResponse.from(user));
    }

    /**
     * Update current user's profile
     *
     * PUT /api/users/me
     */
    @PutMapping("/me")
    @Operation(summary = "Update current user profile", description = "Update the authenticated user's profile information")
    public ResponseEntity<UserProfileResponse> updateCurrentUserProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {

        String auth0Id = jwt.getSubject();

        log.debug("Updating profile for user: {}", auth0Id);

        User user = userService.updateProfile(
                auth0Id,
                request.getFirstName(),
                request.getLastName(),
                request.getPhoneNumber()
        );

        return ResponseEntity.ok(UserProfileResponse.from(user));
    }

    /**
     * Create a new user (for testing/admin purposes)
     *
     * POST /api/users
     */
    @PostMapping
    @Operation(summary = "Create user", description = "Create a new user (for testing/admin purposes)")
    public ResponseEntity<UserProfileResponse> createUser(@Valid @RequestBody Map<String, Object> request) {
        log.debug("Creating user with email: {}", request.get("email"));

        String auth0Id = (String) request.get("auth0Id");
        String email = (String) request.get("email");
        String firstName = (String) request.get("firstName");
        String lastName = (String) request.get("lastName");

        // Create user directly (bypassing Auth0 for testing)
        User user = userService.getOrCreateFromAuth0(auth0Id, Map.of(
                "email", email,
                "given_name", firstName != null ? firstName : "",
                "family_name", lastName != null ? lastName : ""
        ));

        return ResponseEntity.status(201).body(UserProfileResponse.from(user));
    }

    /**
     * Get user profile by ID (admin only - to be secured with @PreAuthorize later)
     *
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Get a user's profile by their ID (admin only)")
    public ResponseEntity<UserProfileResponse> getUserById(@PathVariable Long id) {
        log.debug("Getting user by ID: {}", id);

        return userService.findById(id)
                .map(UserProfileResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}
