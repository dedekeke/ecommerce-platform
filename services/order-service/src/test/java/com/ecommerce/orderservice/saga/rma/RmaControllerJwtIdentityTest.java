package com.ecommerce.orderservice.saga.rma;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Identity-binding and IDOR security tests for {@link RmaController} with the
 * real Spring Security filter chain enabled.
 *
 * <ul>
 *   <li>{@code requestReturn} binds the return owner to the JWT {@code sub} —
 *       the {@code X-User-Id} header is never trusted.</li>
 *   <li>{@code getReturn} enforces ownership: a caller may only read a return
 *       they own, unless they hold {@code SCOPE_admin}.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=true",
        // Required by PromotionServiceClient's boot guard whenever security is on.
        "promotion.service.internal-token=test-internal-service-token",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-tenant.auth0.com/",
        "grpc.server.port=-1"
})
class RmaControllerJwtIdentityTest {

    private static final String USER_A = "auth0|user-a";
    private static final String USER_B = "auth0|user-b";
    private static final String ADMIN = "auth0|admin";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RmaOrchestrator orchestrator;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static Return returnOwnedBy(String userId) {
        return Return.builder()
                .id("rma-1")
                .rmaNumber("RMA-1")
                .orderId("order-1")
                .userId(userId)
                .status(ReturnStatus.RECEIVED)
                .build();
    }

    // ---- requestReturn ------------------------------------------------------

    @Test
    void should_reject_when_requestReturn_headerUserId_differs_from_jwtSub() throws Exception {
        mockMvc.perform(post("/api/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"order-1\",\"reason\":\"size\"}")
                        .header("X-User-Id", USER_B)
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isForbidden());

        verify(orchestrator, never()).requestReturn(any(), any(), any(), any(), any());
    }

    @Test
    void should_bindToJwtSub_when_requestReturn_headerAbsent() throws Exception {
        when(orchestrator.requestReturn(eq("order-1"), eq(USER_A), any(), any(), any()))
                .thenReturn(returnOwnedBy(USER_A));

        mockMvc.perform(post("/api/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"order-1\",\"reason\":\"size\"}")
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isAccepted());

        verify(orchestrator).requestReturn(eq("order-1"), eq(USER_A), any(), any(), any());
    }

    @Test
    void should_allowAdmin_when_requestReturn_headerUserId_differsFromAdminSub() throws Exception {
        when(orchestrator.requestReturn(eq("order-1"), eq(USER_B), any(), any(), any()))
                .thenReturn(returnOwnedBy(USER_B));

        mockMvc.perform(post("/api/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"order-1\",\"reason\":\"size\"}")
                        .header("X-User-Id", USER_B)
                        .with(jwt().jwt(j -> j.subject(ADMIN))
                                .authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
                .andExpect(status().isAccepted());

        verify(orchestrator).requestReturn(eq("order-1"), eq(USER_B), any(), any(), any());
    }

    @Test
    void should_return401_when_requestReturn_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"order-1\",\"reason\":\"size\"}"))
                .andExpect(status().isUnauthorized());

        verify(orchestrator, never()).requestReturn(any(), any(), any(), any(), any());
    }

    // ---- getReturn (IDOR) ---------------------------------------------------

    @Test
    void should_return200_when_getReturn_ownedByCaller() throws Exception {
        when(orchestrator.findById("rma-1")).thenReturn(Optional.of(returnOwnedBy(USER_A)));

        mockMvc.perform(get("/api/returns/rma-1")
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isOk());
    }

    @Test
    void should_return404_when_getReturn_ownedByAnotherUser() throws Exception {
        // Enumeration guard: a non-admin requesting another user's valid RMA id
        // gets the SAME 404 as a missing id (see should_return404_when_getReturn_missing),
        // so 404-vs-403 can't confirm the id exists. Authorization is preserved:
        // the other user's return is never returned.
        when(orchestrator.findById("rma-1")).thenReturn(Optional.of(returnOwnedBy(USER_B)));

        mockMvc.perform(get("/api/returns/rma-1")
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_allowAdmin_when_getReturn_ownedByAnotherUser() throws Exception {
        when(orchestrator.findById("rma-1")).thenReturn(Optional.of(returnOwnedBy(USER_B)));

        mockMvc.perform(get("/api/returns/rma-1")
                        .with(jwt().jwt(j -> j.subject(ADMIN))
                                .authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
                .andExpect(status().isOk());
    }

    @Test
    void should_return404_when_getReturn_missing() throws Exception {
        when(orchestrator.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/returns/missing")
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return401_when_getReturn_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/returns/rma-1"))
                .andExpect(status().isUnauthorized());

        verify(orchestrator, never()).findById(any());
    }
}
