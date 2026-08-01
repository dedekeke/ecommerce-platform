package com.ecommerce.notificationservice.service;

import com.ecommerce.notificationservice.service.push.PushDeliveryException;
import com.ecommerce.notificationservice.service.push.PushProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PushServiceTest {

    private PushProvider pushProvider;
    private PushService pushService;

    @BeforeEach
    void setUp() {
        pushProvider = mock(PushProvider.class);
        pushService = new PushService(pushProvider);
    }

    @Test
    void should_delegateToProvider_when_sendingPush() {
        Map<String, Object> data = Map.of("orderId", "o1");

        pushService.sendPush("token123", "Title", "Body", data);

        verify(pushProvider).send(eq("token123"), eq("Title"), eq("Body"), eq(data));
    }

    @Test
    void should_propagateDeliveryException_when_providerFails() {
        doThrow(new PushDeliveryException("fcm down", new RuntimeException()))
                .when(pushProvider).send(anyString(), anyString(), anyString(), any());

        assertThatThrownBy(() -> pushService.sendPush("token123", "Title", "Body", Map.of()))
                .isInstanceOf(PushDeliveryException.class);
    }
}
