package com.ecommerce.orderservice.subscription;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Local-dev fallback tests for {@link SubscriptionController} with
 * {@code security.enabled=false}. When no JWT principal is present the
 * client-supplied userId (body / path) is honoured so local development
 * against a security-disabled service keeps working — mirroring payment-service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=false",
        "grpc.server.port=-1"
})
class SubscriptionControllerLocalDevIdentityTest {

    private static final String USER_A = "local-user-a";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriptionService subscriptionService;

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

    @Test
    void should_useBodyUserId_when_create_andSecurityDisabled() throws Exception {
        when(subscriptionService.create(any())).thenReturn(subOwnedBy(USER_A));

        mockMvc.perform(post("/api/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_A + "\",\"productId\":\"prod-1\","
                                + "\"quantity\":1,\"intervalDays\":7,\"shippingAddressJson\":\"{}\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<SubscriptionDtos.CreateSubscriptionRequest> captor =
                ArgumentCaptor.forClass(SubscriptionDtos.CreateSubscriptionRequest.class);
        verify(subscriptionService).create(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_A);
    }

    @Test
    void should_usePathUserId_when_listForUser_andSecurityDisabled() throws Exception {
        when(subscriptionService.listForUser(USER_A)).thenReturn(List.of(subOwnedBy(USER_A)));

        mockMvc.perform(get("/api/subscriptions/user/" + USER_A))
                .andExpect(status().isOk());

        verify(subscriptionService).listForUser(eq(USER_A));
    }
}
