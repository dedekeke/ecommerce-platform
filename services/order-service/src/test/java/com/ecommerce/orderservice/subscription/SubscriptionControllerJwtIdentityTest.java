package com.ecommerce.orderservice.subscription;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Identity-binding security tests for {@link SubscriptionController} with the
 * real Spring Security filter chain enabled. The authenticated JWT {@code sub}
 * is the source of truth for subscription ownership; a client-supplied userId
 * (body / path) may never be used to act as another user, unless the caller
 * holds {@code SCOPE_admin}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=true",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-tenant.auth0.com/",
        "grpc.server.port=-1"
})
class SubscriptionControllerJwtIdentityTest {

    private static final String USER_A = "auth0|user-a";
    private static final String USER_B = "auth0|user-b";
    private static final String ADMIN = "auth0|admin";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static Subscription subOwnedBy(String userId) {
        return Subscription.builder()
                .id(1L)
                .userId(userId)
                .productId("prod-1")
                .quantity(1)
                .intervalDays(7)
                .shippingAddressJson("{}")
                .status(SubscriptionStatus.ACTIVE)
                .nextRunAt(LocalDateTime.now())
                .build();
    }

    private static String createBody(String userId) {
        return "{\"userId\":\"" + userId + "\",\"productId\":\"prod-1\",\"quantity\":1,"
                + "\"intervalDays\":7,\"shippingAddressJson\":\"{}\"}";
    }

    // ---- create -------------------------------------------------------------

    @Test
    void should_reject_when_create_bodyUserId_differs_from_jwtSub() throws Exception {
        mockMvc.perform(post("/api/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(USER_B))
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isForbidden());

        verify(subscriptionService, never()).create(any());
    }

    @Test
    void should_bindToJwtSub_when_create_bodyUserId_matches() throws Exception {
        when(subscriptionService.create(any())).thenReturn(subOwnedBy(USER_A));

        mockMvc.perform(post("/api/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(USER_A))
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isCreated());

        ArgumentCaptor<SubscriptionDtos.CreateSubscriptionRequest> captor =
                ArgumentCaptor.forClass(SubscriptionDtos.CreateSubscriptionRequest.class);
        verify(subscriptionService).create(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_A);
    }

    @Test
    void should_allowAdmin_when_create_bodyUserId_differsFromAdminSub() throws Exception {
        when(subscriptionService.create(any())).thenReturn(subOwnedBy(USER_B));

        mockMvc.perform(post("/api/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(USER_B))
                        .with(jwt().jwt(j -> j.subject(ADMIN))
                                .authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
                .andExpect(status().isCreated());

        ArgumentCaptor<SubscriptionDtos.CreateSubscriptionRequest> captor =
                ArgumentCaptor.forClass(SubscriptionDtos.CreateSubscriptionRequest.class);
        verify(subscriptionService).create(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_B);
    }

    @Test
    void should_return401_when_create_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(USER_A)))
                .andExpect(status().isUnauthorized());

        verify(subscriptionService, never()).create(any());
    }

    // ---- listForUser --------------------------------------------------------

    @Test
    void should_reject_when_listForUser_pathUserId_differs_from_jwtSub() throws Exception {
        mockMvc.perform(get("/api/subscriptions/user/" + USER_B)
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isForbidden());

        verify(subscriptionService, never()).listForUser(any());
    }

    @Test
    void should_queryWithJwtSub_when_listForUser_pathUserId_matches() throws Exception {
        when(subscriptionService.listForUser(USER_A)).thenReturn(List.of(subOwnedBy(USER_A)));

        mockMvc.perform(get("/api/subscriptions/user/" + USER_A)
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isOk());

        verify(subscriptionService).listForUser(eq(USER_A));
    }

    @Test
    void should_allowAdmin_when_listForUser_pathUserId_differsFromAdminSub() throws Exception {
        when(subscriptionService.listForUser(USER_B)).thenReturn(List.of());

        mockMvc.perform(get("/api/subscriptions/user/" + USER_B)
                        .with(jwt().jwt(j -> j.subject(ADMIN))
                                .authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
                .andExpect(status().isOk());

        verify(subscriptionService).listForUser(eq(USER_B));
    }

    @Test
    void should_return401_when_listForUser_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/subscriptions/user/" + USER_A))
                .andExpect(status().isUnauthorized());

        verify(subscriptionService, never()).listForUser(any());
    }
}
