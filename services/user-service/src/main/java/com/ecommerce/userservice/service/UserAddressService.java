package com.ecommerce.userservice.service;

import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.domain.UserAddress;
import com.ecommerce.userservice.exception.AddressNotFoundException;
import com.ecommerce.userservice.exception.UserNotFoundException;
import com.ecommerce.userservice.exception.UnauthorizedAccessException;
import com.ecommerce.userservice.repository.UserAddressRepository;
import com.ecommerce.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * User Address Service
 *
 * Business logic for managing user addresses.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAddressService {

    private final UserAddressRepository addressRepository;
    private final UserRepository userRepository;

    /**
     * Get all addresses for a user
     *
     * @param auth0Id The Auth0 user ID
     * @return List of addresses
     */
    @Transactional(readOnly = true)
    public List<UserAddress> getUserAddresses(String auth0Id) {
        return addressRepository.findByUserAuth0Id(auth0Id);
    }

    /**
     * Get a specific address by ID (with security check)
     *
     * @param addressId The address ID
     * @param auth0Id The Auth0 user ID (for security)
     * @return Optional containing the address if found and belongs to the user
     */
    @Transactional(readOnly = true)
    public Optional<UserAddress> getAddress(Long addressId, String auth0Id) {
        User user = userRepository.findByAuth0Id(auth0Id)
                .orElseThrow(() -> UserNotFoundException.byAuth0Id(auth0Id));

        return addressRepository.findByIdAndUserId(addressId, user.getId());
    }

    /**
     * Get the default address for a user
     *
     * @param auth0Id The Auth0 user ID
     * @return Optional containing the default address if found
     */
    @Transactional(readOnly = true)
    public Optional<UserAddress> getDefaultAddress(String auth0Id) {
        User user = userRepository.findByAuth0Id(auth0Id)
                .orElseThrow(() -> UserNotFoundException.byAuth0Id(auth0Id));

        return addressRepository.findDefaultByUserId(user.getId());
    }

    /**
     * Add a new address for a user
     *
     * @param auth0Id The Auth0 user ID
     * @param address The address to add
     * @return The saved address
     */
    @Transactional
    public UserAddress addAddress(String auth0Id, UserAddress address) {
        User user = userRepository.findByAuth0Id(auth0Id)
                .orElseThrow(() -> UserNotFoundException.byAuth0Id(auth0Id));

        address.setUser(user);

        // If this is set as default, clear other defaults
        if (address.getIsDefault()) {
            addressRepository.clearDefaultForUser(user.getId());
        }

        // If this is the user's first address, make it default
        long addressCount = addressRepository.countByUserId(user.getId());
        if (addressCount == 0) {
            address.setIsDefault(true);
        }

        UserAddress savedAddress = addressRepository.save(address);
        log.info("Added address for user: userId={}, addressId={}, label={}",
                user.getId(), savedAddress.getId(), savedAddress.getLabel());

        return savedAddress;
    }

    /**
     * Update an existing address
     *
     * @param addressId The address ID
     * @param auth0Id The Auth0 user ID (for security)
     * @param updatedAddress The updated address data
     * @return The updated address
     */
    @Transactional
    public UserAddress updateAddress(Long addressId, String auth0Id, UserAddress updatedAddress) {
        User user = userRepository.findByAuth0Id(auth0Id)
                .orElseThrow(() -> UserNotFoundException.byAuth0Id(auth0Id));

        UserAddress existingAddress = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> AddressNotFoundException.byIdAndUser(addressId, auth0Id));

        // Update fields
        existingAddress.setLabel(updatedAddress.getLabel());
        existingAddress.setAddress(updatedAddress.getAddress());
        existingAddress.setIsBilling(updatedAddress.getIsBilling());
        existingAddress.setRecipientName(updatedAddress.getRecipientName());
        existingAddress.setRecipientPhone(updatedAddress.getRecipientPhone());

        // If setting as default, clear other defaults
        if (updatedAddress.getIsDefault() && !existingAddress.getIsDefault()) {
            addressRepository.clearDefaultForUser(user.getId());
            existingAddress.setIsDefault(true);
        }

        UserAddress saved = addressRepository.save(existingAddress);
        log.info("Updated address: addressId={}, userId={}", addressId, user.getId());

        return saved;
    }

    /**
     * Set an address as the default
     *
     * @param addressId The address ID
     * @param auth0Id The Auth0 user ID (for security)
     */
    @Transactional
    public void setDefaultAddress(Long addressId, String auth0Id) {
        User user = userRepository.findByAuth0Id(auth0Id)
                .orElseThrow(() -> UserNotFoundException.byAuth0Id(auth0Id));

        UserAddress address = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> AddressNotFoundException.byIdAndUser(addressId, auth0Id));

        // Clear all defaults for this user
        addressRepository.clearDefaultForUser(user.getId());

        // Set this address as default
        address.setIsDefault(true);
        addressRepository.save(address);

        log.info("Set default address: addressId={}, userId={}", addressId, user.getId());
    }

    /**
     * Delete an address
     *
     * @param addressId The address ID
     * @param auth0Id The Auth0 user ID (for security)
     */
    @Transactional
    public void deleteAddress(Long addressId, String auth0Id) {
        User user = userRepository.findByAuth0Id(auth0Id)
                .orElseThrow(() -> UserNotFoundException.byAuth0Id(auth0Id));

        UserAddress address = addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> AddressNotFoundException.byIdAndUser(addressId, auth0Id));

        boolean wasDefault = address.getIsDefault();

        addressRepository.delete(address);
        log.info("Deleted address: addressId={}, userId={}", addressId, user.getId());

        // If we deleted the default address, set the first remaining address as default
        if (wasDefault) {
            List<UserAddress> remainingAddresses = addressRepository.findByUserId(user.getId());
            if (!remainingAddresses.isEmpty()) {
                UserAddress newDefault = remainingAddresses.get(0);
                newDefault.setIsDefault(true);
                addressRepository.save(newDefault);
                log.info("Set new default address after deletion: addressId={}", newDefault.getId());
            }
        }
    }

}
