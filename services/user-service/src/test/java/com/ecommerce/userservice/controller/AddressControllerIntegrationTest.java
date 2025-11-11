package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.domain.Address;
import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.domain.UserAddress;
import com.ecommerce.userservice.domain.UserRole;
import com.ecommerce.userservice.dto.AddAddressRequest;
import com.ecommerce.userservice.repository.UserAddressRepository;
import com.ecommerce.userservice.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Address Controller
 *
 * Uses Testcontainers to spin up a real PostgreSQL database for testing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AddressControllerIntegrationTest {

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
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAddressRepository addressRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String auth0Id;
    private User testUser;
    private UserAddress testAddress;

    @BeforeEach
    void setUp() {
        addressRepository.deleteAll();
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

        // Create a test address
        Address address = Address.builder()
                .street("123 Main St")
                .city("New York")
                .state("NY")
                .postalCode("10001")
                .country("US")
                .build();

        testAddress = UserAddress.builder()
                .user(testUser)
                .label("Home")
                .address(address)
                .isDefault(true)
                .isBilling(false)
                .recipientName("John Doe")
                .recipientPhone("+12125551234")
                .build();
        testAddress = addressRepository.save(testAddress);
    }

    @Test
    void testGetAddresses_shouldReturnAllAddresses() throws Exception {
        mockMvc.perform(get("/api/users/me/addresses")
                        .with(jwt()
                                .jwt(jwt -> jwt.subject(auth0Id))
                                .authorities(new SimpleGrantedAuthority("SCOPE_read:profile"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].label", is("Home")))
                .andExpect(jsonPath("$[0].address.street", is("123 Main St")))
                .andExpect(jsonPath("$[0].address.city", is("New York")))
                .andExpect(jsonPath("$[0].isDefault", is(true)));
    }

    @Test
    void testGetAddressById_existingAddress_shouldReturnAddress() throws Exception {
        mockMvc.perform(get("/api/users/me/addresses/" + testAddress.getId())
                        .with(jwt()
                                .jwt(jwt -> jwt.subject(auth0Id))
                                .authorities(new SimpleGrantedAuthority("SCOPE_read:profile"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.label", is("Home")))
                .andExpect(jsonPath("$.address.street", is("123 Main St")));
    }

    @Test
    void testGetAddressById_nonExistentAddress_shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/users/me/addresses/99999")
                        .with(jwt()
                                .jwt(jwt -> jwt.subject(auth0Id))
                                .authorities(new SimpleGrantedAuthority("SCOPE_read:profile"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetDefaultAddress_shouldReturnDefaultAddress() throws Exception {
        mockMvc.perform(get("/api/users/me/addresses/default")
                        .with(jwt()
                                .jwt(jwt -> jwt.subject(auth0Id))
                                .authorities(new SimpleGrantedAuthority("SCOPE_read:profile"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.label", is("Home")))
                .andExpect(jsonPath("$.isDefault", is(true)));
    }

    @Test
    void testAddAddress_validData_shouldCreateAddress() throws Exception {
        AddAddressRequest request = AddAddressRequest.builder()
                .label("Work")
                .street("456 Office Blvd")
                .city("Boston")
                .state("MA")
                .postalCode("02101")
                .country("US")
                .isDefault(false)
                .isBilling(false)
                .recipientName("John Doe")
                .recipientPhone("+16175551234")
                .build();

        mockMvc.perform(post("/api/users/me/addresses")
                        .with(jwt()
                                .jwt(jwt -> jwt.subject(auth0Id))
                                .authorities(new SimpleGrantedAuthority("SCOPE_write:profile")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.label", is("Work")))
                .andExpect(jsonPath("$.address.street", is("456 Office Blvd")))
                .andExpect(jsonPath("$.address.city", is("Boston")))
                .andExpect(jsonPath("$.isDefault", is(false)));

        // Verify in database
        List<UserAddress> addresses = addressRepository.findByUserId(testUser.getId());
        assertThat(addresses).hasSize(2);
    }

    @Test
    void testAddAddress_invalidData_shouldReturnBadRequest() throws Exception {
        AddAddressRequest request = AddAddressRequest.builder()
                .label("") // Empty label
                .street("456 Office Blvd")
                .city("Boston")
                .state("MA")
                .postalCode("02101")
                .country("USA") // Invalid country code (must be 2 chars)
                .build();

        mockMvc.perform(post("/api/users/me/addresses")
                        .with(jwt()
                                .jwt(jwt -> jwt.subject(auth0Id))
                                .authorities(new SimpleGrantedAuthority("SCOPE_write:profile")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.validationErrors", notNullValue()));
    }

    @Test
    void testUpdateAddress_validData_shouldUpdateAddress() throws Exception {
        AddAddressRequest request = AddAddressRequest.builder()
                .label("Home Updated")
                .street("789 New St")
                .city("New York")
                .state("NY")
                .postalCode("10002")
                .country("US")
                .isDefault(true)
                .isBilling(true)
                .recipientName("John Doe")
                .recipientPhone("+12125559999")
                .build();

        mockMvc.perform(put("/api/users/me/addresses/" + testAddress.getId())
                        .with(jwt()
                                .jwt(jwt -> jwt.subject(auth0Id))
                                .authorities(new SimpleGrantedAuthority("SCOPE_write:profile")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.label", is("Home Updated")))
                .andExpect(jsonPath("$.address.street", is("789 New St")))
                .andExpect(jsonPath("$.isBilling", is(true)));

        // Verify in database
        UserAddress updated = addressRepository.findById(testAddress.getId()).orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getLabel()).isEqualTo("Home Updated");
        assertThat(updated.getAddress().getStreet()).isEqualTo("789 New St");
    }

    @Test
    void testSetDefaultAddress_shouldSetAsDefault() throws Exception {
        // Create another address
        Address address2 = Address.builder()
                .street("456 Office Blvd")
                .city("Boston")
                .state("MA")
                .postalCode("02101")
                .country("US")
                .build();

        UserAddress address2Entity = UserAddress.builder()
                .user(testUser)
                .label("Work")
                .address(address2)
                .isDefault(false)
                .isBilling(false)
                .build();
        address2Entity = addressRepository.save(address2Entity);

        mockMvc.perform(put("/api/users/me/addresses/" + address2Entity.getId() + "/default")
                        .with(jwt()
                                .jwt(jwt -> jwt.subject(auth0Id))
                                .authorities(new SimpleGrantedAuthority("SCOPE_write:profile"))))
                .andExpect(status().isNoContent());

        // Verify the new address is default
        UserAddress updated = addressRepository.findById(address2Entity.getId()).orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getIsDefault()).isTrue();

        // Verify the old default is no longer default
        UserAddress oldDefault = addressRepository.findById(testAddress.getId()).orElse(null);
        assertThat(oldDefault).isNotNull();
        assertThat(oldDefault.getIsDefault()).isFalse();
    }

    @Test
    void testDeleteAddress_shouldDeleteAddress() throws Exception {
        mockMvc.perform(delete("/api/users/me/addresses/" + testAddress.getId())
                        .with(jwt()
                                .jwt(jwt -> jwt.subject(auth0Id))
                                .authorities(new SimpleGrantedAuthority("SCOPE_write:profile"))))
                .andExpect(status().isNoContent());

        // Verify deleted from database
        boolean exists = addressRepository.existsById(testAddress.getId());
        assertThat(exists).isFalse();
    }

    @Test
    void testDeleteAddress_nonExistentAddress_shouldReturnNotFound() throws Exception {
        mockMvc.perform(delete("/api/users/me/addresses/99999")
                        .with(jwt()
                                .jwt(jwt -> jwt.subject(auth0Id))
                                .authorities(new SimpleGrantedAuthority("SCOPE_write:profile"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("ADDRESS_NOT_FOUND")));
    }

}
