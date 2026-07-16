package com.ecommerce.paymentservice.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Maps an internal user to their Stripe Customer id (PR#146 defense-in-depth #2).
 *
 * <p>One Stripe customer per user, so {@code userId} is unique. Attaching this customer to the
 * add-card SetupIntent and the checkout PaymentIntent lets Stripe bind a saved payment method to
 * its owning customer, a second layer behind the server-side ownership check.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "stripe_customers",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_stripe_customer_user", columnNames = {"userId"})
    })
public class StripeCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 128)
    private String userId;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String customerId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
