package com.ecommerce.common.featureflag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EnvVarFeatureFlags}.
 *
 * <p>Tests rely on a Spring {@link MockEnvironment} so we don't need to
 * mutate process environment variables. {@code EnvVarFeatureFlags} accepts
 * a Spring {@link org.springframework.core.env.Environment}, which falls
 * back to {@code System.getenv()} in production via the
 * {@code SystemEnvironmentPropertySource}.
 */
@DisplayName("EnvVarFeatureFlags")
class EnvVarFeatureFlagsTest {

    @Nested
    @DisplayName("isEnabled(name)")
    class BooleanFlag {

        @Test
        @DisplayName("should_returnFalse_when_flagNotSet")
        void should_returnFalse_when_flagNotSet() {
            FeatureFlags flags = new EnvVarFeatureFlags(new MockEnvironment());

            assertThat(flags.isEnabled("UNKNOWN_FLAG")).isFalse();
        }

        @Test
        @DisplayName("should_returnTrue_when_envVarSetToTrue")
        void should_returnTrue_when_envVarSetToTrue() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("FEATURE_FLAG_NEW_CHECKOUT", "true");

            assertThat(new EnvVarFeatureFlags(env).isEnabled("NEW_CHECKOUT")).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
            "true,  true",
            "TRUE,  true",
            "True,  true",
            "false, false",
            "FALSE, false",
            "0,     false",
            "1,     false",
            "yes,   false",
            "'',    false"
        })
        @DisplayName("should_parseValueCaseInsensitively")
        void should_parseValueCaseInsensitively(String raw, boolean expected) {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("FEATURE_FLAG_X", raw);

            assertThat(new EnvVarFeatureFlags(env).isEnabled("X")).isEqualTo(expected);
        }

        @Test
        @DisplayName("should_supportLowercasePropertyName_for_yamlConfig")
        void should_supportLowercasePropertyName_for_yamlConfig() {
            // application.yml uses dotted lowercase: feature.flag.search-typeahead=true
            MockEnvironment env = new MockEnvironment();
            env.setProperty("feature.flag.search-typeahead", "true");

            assertThat(new EnvVarFeatureFlags(env).isEnabled("SEARCH_TYPEAHEAD")).isTrue();
        }
    }

    @Nested
    @DisplayName("isEnabled(name, userId) — percentage rollout")
    class PercentageRollout {

        @Test
        @DisplayName("should_returnFalse_when_neitherFlagNorRolloutSet")
        void should_returnFalse_when_neitherFlagNorRolloutSet() {
            FeatureFlags flags = new EnvVarFeatureFlags(new MockEnvironment());

            assertThat(flags.isEnabled("NEW_PRICING", "user-1")).isFalse();
        }

        @Test
        @DisplayName("should_returnTrue_when_booleanFlagSetToTrue_regardlessOfRollout")
        void should_returnTrue_when_booleanFlagSetToTrue_regardlessOfRollout() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("FEATURE_FLAG_NEW_PRICING", "true");

            assertThat(new EnvVarFeatureFlags(env).isEnabled("NEW_PRICING", "u-anything")).isTrue();
        }

        @Test
        @DisplayName("should_returnFalse_when_rolloutZeroPercent")
        void should_returnFalse_when_rolloutZeroPercent() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("FEATURE_FLAG_ROLLOUT_NEW_PRICING", "0");

            assertThat(new EnvVarFeatureFlags(env).isEnabled("NEW_PRICING", "u-1")).isFalse();
        }

        @Test
        @DisplayName("should_returnTrue_when_rolloutOneHundredPercent")
        void should_returnTrue_when_rolloutOneHundredPercent() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("FEATURE_FLAG_ROLLOUT_NEW_PRICING", "100");

            assertThat(new EnvVarFeatureFlags(env).isEnabled("NEW_PRICING", "u-1")).isTrue();
            assertThat(new EnvVarFeatureFlags(env).isEnabled("NEW_PRICING", "u-2")).isTrue();
            assertThat(new EnvVarFeatureFlags(env).isEnabled("NEW_PRICING", "u-3")).isTrue();
        }

        @Test
        @DisplayName("should_returnSameDecisionForSameUser_acrossInvocations")
        void should_returnSameDecisionForSameUser_acrossInvocations() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("FEATURE_FLAG_ROLLOUT_NEW_PRICING", "50");
            FeatureFlags flags = new EnvVarFeatureFlags(env);

            boolean first = flags.isEnabled("NEW_PRICING", "user-stable");
            boolean second = flags.isEnabled("NEW_PRICING", "user-stable");
            boolean third = flags.isEnabled("NEW_PRICING", "user-stable");

            assertThat(second).isEqualTo(first);
            assertThat(third).isEqualTo(first);
        }

        @Test
        @DisplayName("should_distributeRolloutAcrossUsers_approximatelyToTargetPercentage")
        void should_distributeRolloutAcrossUsers_approximatelyToTargetPercentage() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("FEATURE_FLAG_ROLLOUT_NEW_PRICING", "50");
            FeatureFlags flags = new EnvVarFeatureFlags(env);

            int enabledCount = 0;
            int total = 1000;
            for (int i = 0; i < total; i++) {
                if (flags.isEnabled("NEW_PRICING", "user-" + i)) {
                    enabledCount++;
                }
            }
            // Allow ±10% tolerance — Murmur3 distributes well, this is loose.
            assertThat(enabledCount).isBetween(400, 600);
        }

        @Test
        @DisplayName("should_treatNullUserId_as_disabled_when_rolloutBased")
        void should_treatNullUserId_as_disabled_when_rolloutBased() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("FEATURE_FLAG_ROLLOUT_NEW_PRICING", "100");

            // null userId means we cannot bucket the caller. Fail-closed.
            assertThat(new EnvVarFeatureFlags(env).isEnabled("NEW_PRICING", null)).isFalse();
        }

        @Test
        @DisplayName("should_clampRollout_when_outOfRange")
        void should_clampRollout_when_outOfRange() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("FEATURE_FLAG_ROLLOUT_NEW_PRICING", "9999");
            assertThat(new EnvVarFeatureFlags(env).isEnabled("NEW_PRICING", "u-1")).isTrue();

            env = new MockEnvironment();
            env.setProperty("FEATURE_FLAG_ROLLOUT_NEW_PRICING", "-50");
            assertThat(new EnvVarFeatureFlags(env).isEnabled("NEW_PRICING", "u-1")).isFalse();
        }

        @Test
        @DisplayName("should_ignoreInvalidRolloutValue_andReturnFalse")
        void should_ignoreInvalidRolloutValue_andReturnFalse() {
            MockEnvironment env = new MockEnvironment();
            env.setProperty("FEATURE_FLAG_ROLLOUT_NEW_PRICING", "not-a-number");

            assertThat(new EnvVarFeatureFlags(env).isEnabled("NEW_PRICING", "u-1")).isFalse();
        }
    }

    @Nested
    @DisplayName("input validation")
    class Validation {

        @Test
        @DisplayName("should_returnFalse_when_flagNameIsNullOrBlank")
        void should_returnFalse_when_flagNameIsNullOrBlank() {
            FeatureFlags flags = new EnvVarFeatureFlags(new MockEnvironment());
            assertThat(flags.isEnabled(null)).isFalse();
            assertThat(flags.isEnabled("")).isFalse();
            assertThat(flags.isEnabled("   ")).isFalse();
        }
    }
}
