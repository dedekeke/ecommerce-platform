package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.domain.UserAddress;
import com.ecommerce.userservice.dto.AddAddressRequest;
import com.ecommerce.userservice.dto.AddressResponse;
import com.ecommerce.userservice.service.UserAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Address Controller
 *
 * REST API endpoints for user address management.
 */
@RestController
@RequestMapping("/api/users/me/addresses")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Address", description = "User address management endpoints")
@SecurityRequirement(name = "bearer-jwt")
public class AddressController {

    private final UserAddressService addressService;

    /**
     * Get all addresses for the current user
     *
     * GET /api/users/me/addresses
     */
    @GetMapping
    @Operation(summary = "Get all addresses", description = "Get all addresses for the authenticated user")
    public ResponseEntity<List<AddressResponse>> getAddresses(@AuthenticationPrincipal Jwt jwt) {
        String auth0Id = jwt.getSubject();

        log.debug("Getting addresses for user: {}", auth0Id);

        List<AddressResponse> addresses = addressService.getUserAddresses(auth0Id)
                .stream()
                .map(AddressResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(addresses);
    }

    /**
     * Get a specific address by ID
     *
     * GET /api/users/me/addresses/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get address by ID", description = "Get a specific address by ID")
    public ResponseEntity<AddressResponse> getAddress(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String auth0Id = jwt.getSubject();

        log.debug("Getting address: id={}, user={}", id, auth0Id);

        return addressService.getAddress(id, auth0Id)
                .map(AddressResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get the default address
     *
     * GET /api/users/me/addresses/default
     */
    @GetMapping("/default")
    @Operation(summary = "Get default address", description = "Get the user's default shipping address")
    public ResponseEntity<AddressResponse> getDefaultAddress(@AuthenticationPrincipal Jwt jwt) {
        String auth0Id = jwt.getSubject();

        log.debug("Getting default address for user: {}", auth0Id);

        return addressService.getDefaultAddress(auth0Id)
                .map(AddressResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Add a new address
     *
     * POST /api/users/me/addresses
     */
    @PostMapping
    @Operation(summary = "Add new address", description = "Add a new address for the authenticated user")
    public ResponseEntity<AddressResponse> addAddress(
            @Valid @RequestBody AddAddressRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String auth0Id = jwt.getSubject();

        log.debug("Adding address for user: {}, label={}", auth0Id, request.getLabel());

        UserAddress address = addressService.addAddress(auth0Id, request.toEntity());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AddressResponse.from(address));
    }

    /**
     * Update an existing address
     *
     * PUT /api/users/me/addresses/{id}
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update address", description = "Update an existing address")
    public ResponseEntity<AddressResponse> updateAddress(
            @PathVariable Long id,
            @Valid @RequestBody AddAddressRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String auth0Id = jwt.getSubject();

        log.debug("Updating address: id={}, user={}", id, auth0Id);

        UserAddress address = addressService.updateAddress(id, auth0Id, request.toEntity());

        return ResponseEntity.ok(AddressResponse.from(address));
    }

    /**
     * Set an address as default
     *
     * PUT /api/users/me/addresses/{id}/default
     */
    @PutMapping("/{id}/default")
    @Operation(summary = "Set default address", description = "Set an address as the default shipping address")
    public ResponseEntity<Void> setDefaultAddress(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String auth0Id = jwt.getSubject();

        log.debug("Setting default address: id={}, user={}", id, auth0Id);

        addressService.setDefaultAddress(id, auth0Id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Delete an address
     *
     * DELETE /api/users/me/addresses/{id}
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete address", description = "Delete an address")
    public ResponseEntity<Void> deleteAddress(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String auth0Id = jwt.getSubject();

        log.debug("Deleting address: id={}, user={}", id, auth0Id);

        addressService.deleteAddress(id, auth0Id);

        return ResponseEntity.noContent().build();
    }

}
