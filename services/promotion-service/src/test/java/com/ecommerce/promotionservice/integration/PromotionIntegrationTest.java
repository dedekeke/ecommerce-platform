package com.ecommerce.promotionservice.integration;

import com.ecommerce.promotionservice.dto.PromotionRequest;
import com.ecommerce.promotionservice.dto.PromotionValidationRequest;
import com.ecommerce.promotionservice.model.PromotionType;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
@DisplayName("Promotion Service Integration Tests")
class PromotionIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.2")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        // Override the H2 driver/dialect from application-test.yml — this IT runs
        // against a real MySQL container.
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("SCOPE_admin");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should create promotion successfully")
    void shouldCreatePromotionSuccessfully() throws Exception {
        PromotionRequest request = PromotionRequest.builder()
                .code("TEST20")
                .name("Test 20% Off")
                .description("Test promotion")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20))
                .minPurchaseAmount(BigDecimal.valueOf(100))
                .maxUses(1000)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .active(true)
                .build();

        mockMvc.perform(post("/api/promotions").with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TEST20"))
                .andExpect(jsonPath("$.name").value("Test 20% Off"))
                .andExpect(jsonPath("$.type").value("PERCENTAGE"))
                .andExpect(jsonPath("$.discountValue").value(20))
                .andExpect(jsonPath("$.currentUses").value(0));
    }

    @Test
    @DisplayName("Should fail to create promotion with duplicate code")
    void shouldFailToCreatePromotionWithDuplicateCode() throws Exception {
        PromotionRequest request = PromotionRequest.builder()
                .code("DUPLICATE")
                .name("First Promotion")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .active(true)
                .build();

        mockMvc.perform(post("/api/promotions").with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/promotions").with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    @DisplayName("Should validate promotion successfully")
    void shouldValidatePromotionSuccessfully() throws Exception {
        PromotionRequest createRequest = PromotionRequest.builder()
                .code("VALID20")
                .name("Valid 20% Off")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20))
                .minPurchaseAmount(BigDecimal.valueOf(100))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .active(true)
                .build();

        mockMvc.perform(post("/api/promotions").with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        PromotionValidationRequest validationRequest = PromotionValidationRequest.builder()
                .code("VALID20")
                .purchaseAmount(BigDecimal.valueOf(200))
                .build();

        mockMvc.perform(post("/api/promotions/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.discountAmount").value(40.00))
                .andExpect(jsonPath("$.finalAmount").value(160.00))
                .andExpect(jsonPath("$.promotionCode").value("VALID20"));
    }

    @Test
    @DisplayName("Should fail validation when purchase amount below minimum")
    void shouldFailValidationWhenPurchaseAmountBelowMinimum() throws Exception {
        PromotionRequest createRequest = PromotionRequest.builder()
                .code("MIN100")
                .name("Minimum $100 Promotion")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(15))
                .minPurchaseAmount(BigDecimal.valueOf(100))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .active(true)
                .build();

        mockMvc.perform(post("/api/promotions").with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        PromotionValidationRequest validationRequest = PromotionValidationRequest.builder()
                .code("MIN100")
                .purchaseAmount(BigDecimal.valueOf(50))
                .build();

        mockMvc.perform(post("/api/promotions/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value(containsString("at least")));
    }

    @Test
    @DisplayName("Should apply promotion and increment usage count")
    void shouldApplyPromotionAndIncrementUsageCount() throws Exception {
        PromotionRequest createRequest = PromotionRequest.builder()
                .code("APPLY20")
                .name("Apply 20% Off")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20))
                .minPurchaseAmount(BigDecimal.valueOf(100))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .active(true)
                .build();

        String createResponse = mockMvc.perform(post("/api/promotions").with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentUses").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long promotionId = objectMapper.readTree(createResponse).get("id").asLong();

        PromotionValidationRequest applyRequest = PromotionValidationRequest.builder()
                .code("APPLY20")
                .purchaseAmount(BigDecimal.valueOf(200))
                .build();

        // No service token here: the `test` profile runs security.enabled=false,
        // so this asserts the redemption BEHAVIOUR only. The authorization
        // contract for /apply (service token required) is covered by
        // PromotionControllerSecurityTest with the real filter chain.
        mockMvc.perform(post("/api/promotions/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(applyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.discountAmount").value(40.00));

        mockMvc.perform(get("/api/promotions/" + promotionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentUses").value(1));
    }

    @Test
    @DisplayName("Should update promotion successfully")
    void shouldUpdatePromotionSuccessfully() throws Exception {
        PromotionRequest createRequest = PromotionRequest.builder()
                .code("UPDATE20")
                .name("Original Name")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .active(true)
                .build();

        String createResponse = mockMvc.perform(post("/api/promotions").with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long promotionId = objectMapper.readTree(createResponse).get("id").asLong();

        PromotionRequest updateRequest = PromotionRequest.builder()
                .code("UPDATE20")
                .name("Updated Name")
                .description("Updated description")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(25))
                .minPurchaseAmount(BigDecimal.valueOf(150))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .active(true)
                .build();

        mockMvc.perform(put("/api/promotions/" + promotionId).with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.discountValue").value(25))
                .andExpect(jsonPath("$.minPurchaseAmount").value(150));
    }

    @Test
    @DisplayName("Should delete promotion successfully")
    void shouldDeletePromotionSuccessfully() throws Exception {
        PromotionRequest createRequest = PromotionRequest.builder()
                .code("DELETE20")
                .name("Delete Me")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .active(true)
                .build();

        String createResponse = mockMvc.perform(post("/api/promotions").with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long promotionId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(delete("/api/promotions/" + promotionId).with(jwt().authorities(ADMIN)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/promotions/" + promotionId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should get all active promotions")
    void shouldGetAllActivePromotions() throws Exception {
        PromotionRequest activeRequest = PromotionRequest.builder()
                .code("ACTIVE1")
                .name("Active Promotion")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .active(true)
                .build();

        PromotionRequest inactiveRequest = PromotionRequest.builder()
                .code("INACTIVE1")
                .name("Inactive Promotion")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30))
                .active(false)
                .build();

        mockMvc.perform(post("/api/promotions").with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activeRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/promotions").with(jwt().authorities(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inactiveRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/promotions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("ACTIVE1"));
    }
}
