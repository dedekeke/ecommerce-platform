package com.ecommerce.paymentservice.savedmethod;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Deterministic stub of the Stripe tokenization API for §3.9.
 *
 * <p>The mock derives display fields from the token so tests and dev users
 * get stable, predictable data:
 * <ul>
 *   <li>last4 = trailing 4 digits of the token, left-padded with zeros</li>
 *   <li>brand = derived from any letter found in the token between the
 *       first and last underscore (V→VISA, M→MASTERCARD, A→AMEX, D→DISCOVER,
 *       anything else→VISA). Tokens are conventionally shaped
 *       {@code tok_<brand-letter>_<digits>} so this picks up the brand hint.</li>
 *   <li>expMonth/expYear = current month + 3 years</li>
 * </ul>
 * Replace this bean with {@code StripePaymentProviderAdapter} (real SDK) and
 * the rest of the application is unchanged.</p>
 */
@Slf4j
@Component
public class MockStripeAdapter implements PaymentProviderAdapter {

    static final String PROVIDER_NAME = "STRIPE";

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public AttachResult attachPaymentMethod(String userId, String token) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        String last4 = padLast4(token);
        String brand = brandFor(token);
        LocalDate expiry = LocalDate.now().plusYears(3);
        String providerId = "pm_mock_" + token;
        log.debug("Mock-attached payment method for user={} -> providerId={}", userId, providerId);
        return new AttachResult(providerId, last4, brand,
            expiry.getMonthValue(), expiry.getYear());
    }

    private static String padLast4(String token) {
        String digits = token.replaceAll("\\D", "");
        if (digits.length() >= 4) {
            return digits.substring(digits.length() - 4);
        }
        // Left-pad with zeros so we always return exactly 4 chars.
        return ("0000" + digits).substring(digits.length());
    }

    private static String brandFor(String token) {
        // Pick the first non-"tok" letter — for "tok_M_5555" this is 'M'; for
        // "tok_visa_4242" this is 'v'; for "tok_test_4242" this is 't' (-> VISA).
        String[] parts = token.split("_");
        for (String part : parts) {
            if (part.isBlank() || part.equalsIgnoreCase("tok") || part.equalsIgnoreCase("test")) {
                continue;
            }
            char first = Character.toUpperCase(part.charAt(0));
            if (Character.isLetter(first)) {
                return switch (first) {
                    case 'M' -> "MASTERCARD";
                    case 'A' -> "AMEX";
                    case 'D' -> "DISCOVER";
                    default -> "VISA";
                };
            }
        }
        return "VISA";
    }
}
