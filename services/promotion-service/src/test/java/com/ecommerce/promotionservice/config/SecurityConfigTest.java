package com.ecommerce.promotionservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SecurityConfig Tests")
class SecurityConfigTest {

    /**
     * Booting with security on but no service token would leave
     * {@code POST /api/promotions/apply} permanently unreachable (no presented
     * value can match a blank secret), silently freezing promotion usage
     * counters. Fail fast instead.
     */
    @ParameterizedTest(name = "token=\"{0}\"")
    @ValueSource(strings = {"", "   "})
    @DisplayName("should_failFast_when_securityEnabledAndServiceTokenBlank")
    void should_failFast_when_securityEnabledAndServiceTokenBlank(String token) {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "securityEnabled", true);
        ReflectionTestUtils.setField(config, "internalServiceToken", token);

        assertThatThrownBy(() -> config.securityFilterChain(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INTERNAL_SERVICE_TOKEN");
    }
}
