package com.ecommerce.userservice.controller;

import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.domain.UserRole;
import com.ecommerce.userservice.domain.Wishlist;
import com.ecommerce.userservice.dto.AddWishlistItemRequest;
import com.ecommerce.userservice.repository.UserRepository;
import com.ecommerce.userservice.repository.WishlistRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link WishlistController}. Uses Testcontainers PostgreSQL.
 *
 * <p>Auth model: each request is signed with a JWT whose {@code sub} claim is the
 * Auth0 id of the calling user. The controller and service together enforce
 * that the path {@code userId} corresponds to that subject.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class WishlistControllerIntegrationTest {

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
    private WishlistRepository wishlistRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        wishlistRepository.deleteAll();
        userRepository.deleteAll();

        userA = userRepository.save(User.builder()
                .auth0Id("auth0|userA")
                .email("a@example.com")
                .firstName("Alice")
                .lastName("A")
                .role(UserRole.USER)
                .active(true)
                .lastLoginAt(Instant.now())
                .build());

        userB = userRepository.save(User.builder()
                .auth0Id("auth0|userB")
                .email("b@example.com")
                .firstName("Bob")
                .lastName("B")
                .role(UserRole.USER)
                .active(true)
                .lastLoginAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("should_returnEmptyArray_when_userHasNoWishlistItems")
    void should_returnEmptyArray_when_userHasNoWishlistItems() throws Exception {
        mockMvc.perform(get("/api/wishlist/{userId}", userA.getId())
                        .with(jwtFor("auth0|userA")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("should_returnItem_when_addItemEndpointCalled")
    void should_returnItem_when_addItemEndpointCalled() throws Exception {
        AddWishlistItemRequest body = AddWishlistItemRequest.builder().productId("prod-123").build();

        mockMvc.perform(post("/api/wishlist/{userId}/items", userA.getId())
                        .with(jwtFor("auth0|userA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId", is("prod-123")));

        assertThat(wishlistRepository.existsByUserIdAndProductId(userA.getId(), "prod-123")).isTrue();
    }

    @Test
    @DisplayName("should_beIdempotent_when_sameProductAddedTwice")
    void should_beIdempotent_when_sameProductAddedTwice() throws Exception {
        AddWishlistItemRequest body = AddWishlistItemRequest.builder().productId("prod-dup").build();
        String json = objectMapper.writeValueAsString(body);

        mockMvc.perform(post("/api/wishlist/{userId}/items", userA.getId())
                        .with(jwtFor("auth0|userA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/wishlist/{userId}/items", userA.getId())
                        .with(jwtFor("auth0|userA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        assertThat(wishlistRepository.findByUserIdOrderByAddedAtDesc(userA.getId())).hasSize(1);
    }

    @Test
    @DisplayName("should_return204_when_removeExistingItem")
    void should_return204_when_removeExistingItem() throws Exception {
        wishlistRepository.save(Wishlist.builder()
                .userId(userA.getId())
                .productId("prod-x")
                .addedAt(Instant.now())
                .build());

        mockMvc.perform(delete("/api/wishlist/{userId}/items/{productId}", userA.getId(), "prod-x")
                        .with(jwtFor("auth0|userA")))
                .andExpect(status().isNoContent());

        assertThat(wishlistRepository.existsByUserIdAndProductId(userA.getId(), "prod-x")).isFalse();
    }

    @Test
    @DisplayName("should_return404_when_removingMissingItem")
    void should_return404_when_removingMissingItem() throws Exception {
        mockMvc.perform(delete("/api/wishlist/{userId}/items/{productId}", userA.getId(), "ghost")
                        .with(jwtFor("auth0|userA")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should_return403_when_userATriesToReadUserBsWishlist")
    void should_return403_when_userATriesToReadUserBsWishlist() throws Exception {
        // userA holds the JWT; tries to GET userB's wishlist
        mockMvc.perform(get("/api/wishlist/{userId}", userB.getId())
                        .with(jwtFor("auth0|userA")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should_return403_when_userATriesToAddItemToUserBsWishlist")
    void should_return403_when_userATriesToAddItemToUserBsWishlist() throws Exception {
        AddWishlistItemRequest body = AddWishlistItemRequest.builder().productId("prod-x").build();

        mockMvc.perform(post("/api/wishlist/{userId}/items", userB.getId())
                        .with(jwtFor("auth0|userA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        assertThat(wishlistRepository.findByUserIdOrderByAddedAtDesc(userB.getId())).isEmpty();
    }

    @Test
    @DisplayName("should_return400_when_addItemRequestHasBlankProductId")
    void should_return400_when_addItemRequestHasBlankProductId() throws Exception {
        AddWishlistItemRequest body = AddWishlistItemRequest.builder().productId("").build();

        mockMvc.perform(post("/api/wishlist/{userId}/items", userA.getId())
                        .with(jwtFor("auth0|userA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(String auth0Id) {
        return jwt()
                .jwt(builder -> builder.subject(auth0Id))
                .authorities(new SimpleGrantedAuthority("SCOPE_read:profile"));
    }
}
