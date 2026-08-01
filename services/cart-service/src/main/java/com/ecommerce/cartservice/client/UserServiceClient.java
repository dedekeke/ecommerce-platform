package com.ecommerce.cartservice.client;

import com.ecommerce.cartservice.dto.UserContactDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for User Service — resolves a shopper's contact details by
 * Auth0 {@code sub} so the cart can denormalise their email for abandonment
 * reminders.
 */
@FeignClient(name = "user-service")
public interface UserServiceClient {

    @GetMapping("/api/users/by-auth0/{sub}")
    UserContactDto getUserByAuth0Id(@PathVariable("sub") String sub);
}
