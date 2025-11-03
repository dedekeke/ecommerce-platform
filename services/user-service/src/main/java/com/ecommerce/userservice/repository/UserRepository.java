package com.ecommerce.userservice.repository;

import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * User Repository
 *
 * Provides data access methods for User entities.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by Auth0 ID (sub claim from JWT)
     *
     * @param auth0Id The Auth0 user ID
     * @return Optional containing the user if found
     */
    Optional<User> findByAuth0Id(String auth0Id);

    /**
     * Find user by email
     *
     * @param email The user's email address
     * @return Optional containing the user if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if a user exists by Auth0 ID
     *
     * @param auth0Id The Auth0 user ID
     * @return true if user exists, false otherwise
     */
    boolean existsByAuth0Id(String auth0Id);

    /**
     * Check if a user exists by email
     *
     * @param email The email address
     * @return true if user exists, false otherwise
     */
    boolean existsByEmail(String email);

    /**
     * Find all users by role
     *
     * @param role The user role
     * @return List of users with the specified role
     */
    List<User> findByRole(UserRole role);

    /**
     * Find all active users
     *
     * @param active The active status
     * @return List of active/inactive users
     */
    List<User> findByActive(Boolean active);

    /**
     * Find users by role and active status
     *
     * @param role The user role
     * @param active The active status
     * @return List of users matching the criteria
     */
    List<User> findByRoleAndActive(UserRole role, Boolean active);

    /**
     * Find users who haven't logged in since a given date
     *
     * @param since The date to compare against
     * @return List of inactive users
     */
    @Query("SELECT u FROM User u WHERE u.lastLoginAt IS NULL OR u.lastLoginAt < :since")
    List<User> findInactiveUsersSince(@Param("since") Instant since);

    /**
     * Find users with verified emails
     *
     * @param emailVerified The email verification status
     * @return List of users with matching email verification status
     */
    List<User> findByEmailVerified(Boolean emailVerified);

    /**
     * Count users by role
     *
     * @param role The user role
     * @return Number of users with the specified role
     */
    long countByRole(UserRole role);

    /**
     * Count active users
     *
     * @return Number of active users
     */
    long countByActive(Boolean active);

}
