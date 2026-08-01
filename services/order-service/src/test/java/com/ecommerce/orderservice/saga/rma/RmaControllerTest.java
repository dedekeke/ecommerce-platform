package com.ecommerce.orderservice.saga.rma;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RmaController.class,
    excludeAutoConfiguration = {
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        DataSourceAutoConfiguration.class
    })
@Import({RmaControllerTest.TestJwtConfig.class, RmaController.class,
    com.ecommerce.orderservice.security.UserIdentityResolver.class})
@TestPropertySource(properties = {
    "security.enabled=true",
    "spring.cloud.discovery.enabled=false",
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test.auth0.com/",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://test.auth0.com/.well-known/jwks.json"
})
@DisplayName("RmaController")
class RmaControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private RmaOrchestrator orchestrator;

    // ---------- POST /api/returns ----------

    @Test
    @DisplayName("requestReturn_unauthenticated_isDenied")
    void requestReturn_unauthenticated_isDenied() throws Exception {
        mockMvc.perform(post("/api/returns").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("orderId", "order-1", "reason", "size"))))
            .andExpect(status().is4xxClientError());

        verify(orchestrator, never()).requestReturn(anyString(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_user")
    @DisplayName("requestReturn_authenticated_returns202_withLocationAndRmaId")
    void requestReturn_authenticated_returns202_withLocationAndRmaId() throws Exception {
        Return rma = Return.builder()
            .id("rma-1").rmaNumber("RMA-XYZ").orderId("order-1")
            .status(ReturnStatus.AWAITING_SHIPMENT).build();
        when(orchestrator.requestReturn(eq("order-1"), any(), eq("size"), any(), any())).thenReturn(rma);

        mockMvc.perform(post("/api/returns").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("orderId", "order-1", "reason", "size"))))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", "/api/returns/rma-1"))
            .andExpect(jsonPath("$.id").value("rma-1"))
            .andExpect(jsonPath("$.rmaNumber").value("RMA-XYZ"));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_user")
    @DisplayName("requestReturn_withLines_passesLineRequests")
    void requestReturn_withLines_passesLineRequests() throws Exception {
        Return rma = Return.builder()
            .id("rma-2").rmaNumber("RMA-LINES").orderId("order-1")
            .status(ReturnStatus.AWAITING_SHIPMENT).build();
        when(orchestrator.requestReturn(eq("order-1"), any(), any(), any(), any())).thenReturn(rma);

        String body = objectMapper.writeValueAsString(Map.of(
            "orderId", "order-1",
            "reason", "partial",
            "lines", List.of(Map.of("orderItemId", "item-1", "quantity", 2, "reason", "broken"))));

        mockMvc.perform(post("/api/returns").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted());

        org.mockito.ArgumentCaptor<List<RmaOrchestrator.LineRequest>> linesCaptor =
            org.mockito.ArgumentCaptor.forClass(List.class);
        verify(orchestrator).requestReturn(eq("order-1"), any(), eq("partial"),
            linesCaptor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(linesCaptor.getValue()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(linesCaptor.getValue().get(0).orderItemId())
            .isEqualTo("item-1");
    }

    @Test
    @WithMockUser(authorities = "SCOPE_user")
    @DisplayName("requestReturn_blankOrderId_returns400")
    void requestReturn_blankOrderId_returns400() throws Exception {
        mockMvc.perform(post("/api/returns").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("orderId", "", "reason", "x"))))
            .andExpect(status().isBadRequest());

        verify(orchestrator, never()).requestReturn(anyString(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_user")
    @DisplayName("requestReturn_orchestratorThrowsRmaException_returns400")
    void requestReturn_orchestratorThrowsRmaException_returns400() throws Exception {
        when(orchestrator.requestReturn(anyString(), any(), any(), any(), any()))
            .thenThrow(new RmaException("Return window has expired"));

        mockMvc.perform(post("/api/returns").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("orderId", "order-1", "reason", "x"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Return window has expired"));
    }

    // ---------- GET /api/returns (admin list) ----------

    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    @DisplayName("listReturns_admin_returnsPageResponse")
    void listReturns_admin_returnsPageResponse() throws Exception {
        ReturnSummary summary = new ReturnSummary("rma-1", "RMA-1", "ord-1", "user-1",
            ReturnStatus.RECEIVED, null, null,
            java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(orchestrator.listReturns(any(), any())).thenReturn(
            new org.springframework.data.domain.PageImpl<>(List.of(summary),
                org.springframework.data.domain.PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/returns"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].rmaNumber").value("RMA-1"))
            .andExpect(jsonPath("$.content[0].status").value("RECEIVED"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    @DisplayName("listReturns_statusFilter_passedWithRequestedAtDescSort")
    void listReturns_statusFilter_passedToOrchestrator() throws Exception {
        when(orchestrator.listReturns(any(), any())).thenReturn(
            new org.springframework.data.domain.PageImpl<>(List.of(),
                org.springframework.data.domain.PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/returns?status=AWAITING_SHIPMENT"))
            .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
            org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(orchestrator).listReturns(eq(ReturnStatus.AWAITING_SHIPMENT), captor.capture());
        org.assertj.core.api.Assertions.assertThat(
                captor.getValue().getSort().getOrderFor("requestedAt"))
            .isNotNull()
            .satisfies(o -> org.assertj.core.api.Assertions.assertThat(o.isDescending()).isTrue());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_user")
    @DisplayName("listReturns_nonAdmin_returns403")
    void listReturns_nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/returns"))
            .andExpect(status().isForbidden());
        verify(orchestrator, never()).listReturns(any(), any());
    }

    @Test
    @DisplayName("listReturns_unauthenticated_isDenied")
    void listReturns_unauthenticated_isDenied() throws Exception {
        mockMvc.perform(get("/api/returns"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    @DisplayName("listReturns_collectionPathNotShadowedByDetailPath")
    void listReturns_collectionPathNotShadowedByDetail() throws Exception {
        // GET /api/returns resolves to the list handler...
        when(orchestrator.listReturns(any(), any())).thenReturn(
            new org.springframework.data.domain.PageImpl<>(List.of(),
                org.springframework.data.domain.PageRequest.of(0, 20), 0));
        mockMvc.perform(get("/api/returns")).andExpect(status().isOk());

        // ...while GET /api/returns/{id} still resolves to the by-id handler.
        when(orchestrator.findById("rma-9")).thenReturn(Optional.of(
            Return.builder().id("rma-9").rmaNumber("RMA-9").userId("user-1")
                .status(ReturnStatus.RECEIVED).build()));
        mockMvc.perform(get("/api/returns/rma-9"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("rma-9"));

        verify(orchestrator).listReturns(any(), any());
        verify(orchestrator).findById("rma-9");
    }

    // ---------- GET /api/returns/{id} ----------

    @Test
    @WithMockUser
    @DisplayName("getReturn_returns404_whenMissing")
    void getReturn_returns404_whenMissing() throws Exception {
        when(orchestrator.findById("missing")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/returns/missing")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("getReturn_returnsBody_whenFound")
    void getReturn_returnsBody_whenFound() throws Exception {
        Return rma = Return.builder()
            .id("rma-1").rmaNumber("RMA-1").status(ReturnStatus.RECEIVED).build();
        when(orchestrator.findById("rma-1")).thenReturn(Optional.of(rma));

        mockMvc.perform(get("/api/returns/rma-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("rma-1"))
            .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    @DisplayName("getReturn_unauthenticated_isDenied")
    void getReturn_unauthenticated_isDenied() throws Exception {
        mockMvc.perform(get("/api/returns/anything"))
            .andExpect(status().is4xxClientError());
    }

    // ---------- GET /api/returns/user/{id} ----------

    @Test
    @WithMockUser(username = "user-1", authorities = "SCOPE_user")
    @DisplayName("getUserReturns_sameUser_returnsList")
    void getUserReturns_sameUser_returnsList() throws Exception {
        when(orchestrator.findByUser("user-1"))
            .thenReturn(List.of(Return.builder().id("a").rmaNumber("RMA-A").build()));

        mockMvc.perform(get("/api/returns/user/user-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("a"));
    }

    @Test
    @WithMockUser(username = "user-2", authorities = "SCOPE_user")
    @DisplayName("getUserReturns_differentUserNonAdmin_returns403")
    void getUserReturns_differentUserNonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/returns/user/user-1"))
            .andExpect(status().isForbidden());

        verify(orchestrator, never()).findByUser(anyString());
    }

    @Test
    @WithMockUser(username = "admin", authorities = "SCOPE_admin")
    @DisplayName("getUserReturns_admin_canViewAnyUser")
    void getUserReturns_admin_canViewAnyUser() throws Exception {
        when(orchestrator.findByUser("user-1")).thenReturn(List.of());
        mockMvc.perform(get("/api/returns/user/user-1")).andExpect(status().isOk());
    }

    // ---------- POST /api/returns/{id}/receive ----------

    @Test
    @WithMockUser(authorities = "SCOPE_user")
    @DisplayName("receive_nonAdmin_returns403")
    void receive_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/returns/rma-1/receive").with(csrf()))
            .andExpect(status().isForbidden());

        verify(orchestrator, never()).markReceived(anyString());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    @DisplayName("receive_admin_returnsUpdatedRma")
    void receive_admin_returnsUpdatedRma() throws Exception {
        Return rma = Return.builder()
            .id("rma-1").rmaNumber("RMA-1").status(ReturnStatus.RECEIVED).build();
        when(orchestrator.markReceived("rma-1")).thenReturn(rma);

        mockMvc.perform(post("/api/returns/rma-1/receive").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    // ---------- POST /api/returns/{id}/inspect ----------

    @Test
    @WithMockUser(authorities = "SCOPE_user")
    @DisplayName("inspect_nonAdmin_returns403")
    void inspect_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/returns/rma-1/inspect").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("outcome", "APPROVED"))))
            .andExpect(status().isForbidden());

        verify(orchestrator, never()).inspect(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    @DisplayName("inspect_admin_returnsUpdatedRma")
    void inspect_admin_returnsUpdatedRma() throws Exception {
        Return rma = Return.builder()
            .id("rma-1").rmaNumber("RMA-1").status(ReturnStatus.COMPLETED).outcome("APPROVED").build();
        when(orchestrator.inspect(eq("rma-1"), eq("APPROVED"), eq("OPENED"), eq("ok"), any(), any()))
            .thenReturn(rma);

        mockMvc.perform(post("/api/returns/rma-1/inspect").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("outcome", "APPROVED", "condition", "OPENED", "notes", "ok"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.outcome").value("APPROVED"));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    @DisplayName("inspect_admin_passesRestockingFee")
    void inspect_admin_passesRestockingFee() throws Exception {
        Return rma = Return.builder()
            .id("rma-1").rmaNumber("RMA-1").status(ReturnStatus.COMPLETED).outcome("APPROVED").build();
        when(orchestrator.inspect(eq("rma-1"), eq("APPROVED"), any(), any(),
            eq(new java.math.BigDecimal("15.00")), any())).thenReturn(rma);

        mockMvc.perform(post("/api/returns/rma-1/inspect").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("outcome", "APPROVED", "restockingFeePercent", "15.00"))))
            .andExpect(status().isOk());

        verify(orchestrator).inspect(eq("rma-1"), eq("APPROVED"), any(), any(),
            eq(new java.math.BigDecimal("15.00")), any());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    @DisplayName("inspect_restockingFeeOver100_returns400")
    void inspect_restockingFeeOver100_returns400() throws Exception {
        mockMvc.perform(post("/api/returns/rma-1/inspect").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("outcome", "APPROVED", "restockingFeePercent", "150"))))
            .andExpect(status().isBadRequest());

        verify(orchestrator, never()).inspect(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    @DisplayName("inspect_blankOutcome_returns400")
    void inspect_blankOutcome_returns400() throws Exception {
        mockMvc.perform(post("/api/returns/rma-1/inspect").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("outcome", ""))))
            .andExpect(status().isBadRequest());
    }

    /**
     * Provide a stub JwtDecoder so the OAuth2 resource server can wire even
     * though we never actually validate a real token. Also activates
     * @PreAuthorize processing for the @WebMvcTest slice.
     */
    @Configuration
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity(prePostEnabled = true)
    static class TestJwtConfig {
        @Bean
        @Primary
        public JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }
    }
}
