package com.ecommerce.userservice.repository;

import com.ecommerce.userservice.domain.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * User Address Repository
 *
 * Provides data access methods for UserAddress entities.
 */
@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    /**
     * Find all addresses for a specific user
     *
     * @param userId The user ID
     * @return List of addresses belonging to the user
     */
    @Query("SELECT ua FROM UserAddress ua WHERE ua.user.id = :userId ORDER BY ua.isDefault DESC, ua.createdAt DESC")
    List<UserAddress> findByUserId(@Param("userId") Long userId);

    /**
     * Find all addresses for a user by Auth0 ID
     *
     * @param auth0Id The Auth0 user ID
     * @return List of addresses
     */
    @Query("SELECT ua FROM UserAddress ua WHERE ua.user.auth0Id = :auth0Id ORDER BY ua.isDefault DESC, ua.createdAt DESC")
    List<UserAddress> findByUserAuth0Id(@Param("auth0Id") String auth0Id);

    /**
     * Find the default address for a user
     *
     * @param userId The user ID
     * @return Optional containing the default address if found
     */
    @Query("SELECT ua FROM UserAddress ua WHERE ua.user.id = :userId AND ua.isDefault = true")
    Optional<UserAddress> findDefaultByUserId(@Param("userId") Long userId);

    /**
     * Find a specific address by ID and user ID (for security)
     *
     * @param id The address ID
     * @param userId The user ID
     * @return Optional containing the address if found and belongs to the user
     */
    @Query("SELECT ua FROM UserAddress ua WHERE ua.id = :id AND ua.user.id = :userId")
    Optional<UserAddress> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Find all billing addresses for a user
     *
     * @param userId The user ID
     * @return List of billing addresses
     */
    @Query("SELECT ua FROM UserAddress ua WHERE ua.user.id = :userId AND ua.isBilling = true")
    List<UserAddress> findBillingAddressesByUserId(@Param("userId") Long userId);

    /**
     * Clear all default flags for a user's addresses (used when setting a new default)
     *
     * @param userId The user ID
     */
    @Modifying
    @Query("UPDATE UserAddress ua SET ua.isDefault = false WHERE ua.user.id = :userId")
    void clearDefaultForUser(@Param("userId") Long userId);

    /**
     * Count addresses for a user
     *
     * @param userId The user ID
     * @return Number of addresses
     */
    long countByUserId(Long userId);

    /**
     * Delete all addresses for a user
     *
     * @param userId The user ID
     */
    void deleteByUserId(Long userId);

}
