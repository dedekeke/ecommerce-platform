package com.ecommerce.recommendationservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks the set of distinct products a user has purchased.
 *
 * <p>Used by personalised recommendations to (a) build the candidate pool
 * (every product co-occurrent with anything the user owns) and (b) exclude
 * products the user already owns from the response.
 */
@Document(collection = "user_purchases")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPurchaseDocument {

    @Id
    private String userId;

    @Builder.Default
    private Set<String> productIds = new HashSet<>();

    private Instant updatedAt;
}
