package com.ecommerce.userservice.service;

import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.domain.UserRole;
import com.ecommerce.userservice.exception.UserNotFoundException;
import com.ecommerce.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * User Service
 *
 * Business logic for user management and Auth0 synchronization.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    /**
     * Get or create user from Auth0 JWT claims.
     * This is called on first login to sync the user from Auth0.
     *
     * @param auth0Id Auth0 user ID (sub claim)
     * @param claims JWT claims from Auth0 token
     * @return The user (existing or newly created)
     */
    @Transactional
    public User getOrCreateFromAuth0(String auth0Id, Map<String, Object> claims) {
        log.debug("Getting or creating user for Auth0 ID: {}", auth0Id);

        return userRepository.findByAuth0Id(auth0Id)
                .map(user -> {
                    log.debug("User found, updating last login: {}", auth0Id);
                    user.updateLastLogin();
                    return userRepository.save(user);
                })
                .orElseGet(() -> {
                    log.info("Creating new user from Auth0: {}", auth0Id);
                    return createUserFromAuth0Claims(auth0Id, claims);
                });
    }

    /**
     * Create a new user from Auth0 claims
     *
     * @param auth0Id Auth0 user ID
     * @param claims JWT claims
     * @return The newly created user
     */
    private User createUserFromAuth0Claims(String auth0Id, Map<String, Object> claims) {
        User user = User.builder()
                .auth0Id(auth0Id)
                .email(extractEmail(claims))
                .firstName(extractFirstName(claims))
                .lastName(extractLastName(claims))
                .emailVerified(extractEmailVerified(claims))
                .pictureUrl(extractPictureUrl(claims))
                .role(extractRole(claims))
                .active(true)
                .lastLoginAt(Instant.now())
                .build();

        User savedUser = userRepository.save(user);
        log.info("Created new user: id={}, email={}, auth0Id={}",
                savedUser.getId(), savedUser.getEmail(), savedUser.getAuth0Id());

        return savedUser;
    }

    /**
     * Find user by Auth0 ID
     *
     * @param auth0Id The Auth0 user ID
     * @return Optional containing the user if found
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "users-by-auth0", key = "#auth0Id", unless = "#result == null")
    public Optional<User> findByAuth0Id(String auth0Id) {
        log.debug("Cache miss - fetching user from DB for auth0Id: {}", auth0Id);
        return userRepository.findByAuth0Id(auth0Id);
    }

    /**
     * Find user by ID
     *
     * @param id The user ID
     * @return Optional containing the user if found
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#id", unless = "#result == null")
    public Optional<User> findById(Long id) {
        log.debug("Cache miss - fetching user from DB for id: {}", id);
        return userRepository.findById(id);
    }

    /**
     * Find user by email
     *
     * @param email The email address
     * @return Optional containing the user if found
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "users-by-email", key = "#email", unless = "#result == null")
    public Optional<User> findByEmail(String email) {
        log.debug("Cache miss - fetching user from DB for email: {}", email);
        return userRepository.findByEmail(email);
    }

    /**
     * Update user profile
     *
     * @param user The user to update
     * @return The updated user
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "users", key = "#user.id"),
        @CacheEvict(value = "users-by-auth0", key = "#user.auth0Id"),
        @CacheEvict(value = "users-by-email", key = "#user.email")
    })
    public User updateUser(User user) {
        log.debug("Updating user: id={}", user.getId());
        return userRepository.save(user);
    }

    /**
     * Update user's profile information
     *
     * @param auth0Id The Auth0 user ID
     * @param firstName The new first name
     * @param lastName The new last name
     * @param phoneNumber The new phone number
     * @return The updated user
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "users", allEntries = true),
        @CacheEvict(value = "users-by-auth0", key = "#auth0Id"),
        @CacheEvict(value = "users-by-email", allEntries = true)
    })
    public User updateProfile(String auth0Id, String firstName, String lastName, String phoneNumber) {
        User user = userRepository.findByAuth0Id(auth0Id)
                .orElseThrow(() -> UserNotFoundException.byAuth0Id(auth0Id));

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhoneNumber(phoneNumber);

        User updatedUser = userRepository.save(user);
        log.info("Updated profile for user: id={}, auth0Id={}", updatedUser.getId(), auth0Id);

        return updatedUser;
    }

    /**
     * Deactivate a user account
     *
     * @param userId The user ID
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "users", key = "#userId"),
        @CacheEvict(value = "users-by-auth0", allEntries = true),
        @CacheEvict(value = "users-by-email", allEntries = true)
    })
    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.byId(userId));

        user.setActive(false);
        userRepository.save(user);
        log.info("Deactivated user: id={}", userId);
    }

    /**
     * Reactivate a user account
     *
     * @param userId The user ID
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "users", key = "#userId"),
        @CacheEvict(value = "users-by-auth0", allEntries = true),
        @CacheEvict(value = "users-by-email", allEntries = true)
    })
    public void reactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.byId(userId));

        user.setActive(true);
        userRepository.save(user);
        log.info("Reactivated user: id={}", userId);
    }

    /**
     * Change user role (admin operation)
     *
     * @param userId The user ID
     * @param newRole The new role
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "users", key = "#userId"),
        @CacheEvict(value = "users-by-auth0", allEntries = true),
        @CacheEvict(value = "users-by-email", allEntries = true)
    })
    public void changeUserRole(Long userId, UserRole newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.byId(userId));

        UserRole oldRole = user.getRole();
        user.setRole(newRole);
        userRepository.save(user);
        log.info("Changed user role: id={}, oldRole={}, newRole={}", userId, oldRole, newRole);
    }

    /**
     * Get all users by role
     *
     * @param role The user role
     * @return List of users with the specified role
     */
    @Transactional(readOnly = true)
    public List<User> findByRole(UserRole role) {
        return userRepository.findByRole(role);
    }

    /**
     * Get total user count
     *
     * @return Total number of users
     */
    @Transactional(readOnly = true)
    public long getTotalUserCount() {
        return userRepository.count();
    }

    /**
     * Get active user count
     *
     * @return Number of active users
     */
    @Transactional(readOnly = true)
    public long getActiveUserCount() {
        return userRepository.countByActive(true);
    }

    // Helper methods to extract claims from Auth0 token

    private String extractEmail(Map<String, Object> claims) {
        return (String) claims.getOrDefault("email", "");
    }

    private String extractFirstName(Map<String, Object> claims) {
        // Auth0 might have "given_name" or "name"
        String givenName = (String) claims.get("given_name");
        if (givenName != null) {
            return givenName;
        }

        String fullName = (String) claims.get("name");
        if (fullName != null && fullName.contains(" ")) {
            return fullName.split(" ")[0];
        }

        return null;
    }

    private String extractLastName(Map<String, Object> claims) {
        // Auth0 might have "family_name" or parse from "name"
        String familyName = (String) claims.get("family_name");
        if (familyName != null) {
            return familyName;
        }

        String fullName = (String) claims.get("name");
        if (fullName != null && fullName.contains(" ")) {
            String[] parts = fullName.split(" ");
            return parts[parts.length - 1];
        }

        return null;
    }

    private Boolean extractEmailVerified(Map<String, Object> claims) {
        Object emailVerified = claims.get("email_verified");
        if (emailVerified instanceof Boolean) {
            return (Boolean) emailVerified;
        }
        return false;
    }

    private String extractPictureUrl(Map<String, Object> claims) {
        return (String) claims.get("picture");
    }

    private UserRole extractRole(Map<String, Object> claims) {
        // Check for roles in the token
        // Auth0 might store roles in different claim names depending on configuration
        Object rolesObj = claims.get("https://ecommerce-platform.com/roles");
        if (rolesObj == null) {
            rolesObj = claims.get("roles");
        }

        if (rolesObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) rolesObj;
            if (roles.contains("ADMIN")) {
                return UserRole.ADMIN;
            }
            if (roles.contains("WAREHOUSE_MANAGER")) {
                return UserRole.WAREHOUSE_MANAGER;
            }
            if (roles.contains("SUPPORT")) {
                return UserRole.SUPPORT;
            }
        }

        // Default to USER role
        return UserRole.USER;
    }

}
