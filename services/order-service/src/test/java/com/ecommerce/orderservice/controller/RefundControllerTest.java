package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.saga.refund.RefundOrchestrator;
import com.ecommerce.orderservice.saga.refund.RefundSagaState;
import com.ecommerce.orderservice.saga.refund.RefundSagaStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
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

@WebMvcTest(controllers = RefundController.class,
    excludeAutoConfiguration = {
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        DataSourceAutoConfiguration.class
    })
@Import({RefundControllerTest.TestJwtConfig.class, RefundController.class})
@TestPropertySource(properties = {
    "security.enabled=true",
    "spring.cloud.discovery.enabled=false",
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test.auth0.com/",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://test.auth0.com/.well-known/jwks.json"
})
class RefundControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private RefundOrchestrator orchestrator;

    @Test
    void startRefund_unauthenticated_isDenied() throws Exception {
        mockMvc.perform(post("/api/orders/order-1/refund").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "size"))))
            .andExpect(status().is4xxClientError());

        verify(orchestrator, never()).startRefund(any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_user")
    void startRefund_nonAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/orders/order-1/refund").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "size"))))
            .andExpect(status().isForbidden());

        verify(orchestrator, never()).startRefund(any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    void startRefund_admin_returns202_withLocationAndSagaId() throws Exception {
        RefundSagaState state = RefundSagaState.builder()
            .id("saga-1").orderId("order-1").status(RefundSagaStatus.PENDING).build();
        when(orchestrator.startRefund(eq("order-1"), eq("size"), any())).thenReturn(state);

        mockMvc.perform(post("/api/orders/order-1/refund").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "size"))))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", "/api/orders/refunds/saga-1"))
            .andExpect(jsonPath("$.id").value("saga-1"));

        verify(orchestrator).startRefund(eq("order-1"), eq("size"), any());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    void getRefund_returnsSagaState_whenFound() throws Exception {
        RefundSagaState state = RefundSagaState.builder()
            .id("saga-1").orderId("order-1").status(RefundSagaStatus.COMPLETED).build();
        when(orchestrator.findSaga("saga-1")).thenReturn(Optional.of(state));

        mockMvc.perform(get("/api/orders/refunds/saga-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("saga-1"))
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    void getRefund_returns404_whenMissing() throws Exception {
        when(orchestrator.findSaga("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/orders/refunds/missing"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getRefund_unauthenticated_isDenied() throws Exception {
        mockMvc.perform(get("/api/orders/refunds/anything"))
            .andExpect(status().is4xxClientError());
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
