package com.ecommerce.common.featureflag;

import org.springframework.core.env.Environment;

/**
 * Environment-variable-backed implementation of {@link FeatureFlags}.
 *
 * <p>This is a deliberate stop-gap until the platform crosses the
 * ~10-active-flags threshold (see {@code docs/FEATURE_FLAGS.md}) and switches
 * to a real provider (Unleash). It supports two patterns:
 * <ul>
 *   <li><strong>Boolean toggles</strong> — {@code FEATURE_FLAG_<NAME>=true|false}</li>
 *   <li><strong>Percentage rollout</strong> — {@code FEATURE_FLAG_ROLLOUT_<NAME>=0..100}.
 *       The user's id is hashed (Murmur3) and bucketed into the {@code [0,100)}
 *       space; a user is enabled iff their bucket is strictly less than the
 *       configured percentage. The hash is deterministic, so a user's bucket
 *       never moves between calls.</li>
 * </ul>
 *
 * <p>We read from a Spring {@link Environment} rather than {@code System.getenv()}
 * directly so callers can also configure flags via {@code application.yml}
 * (e.g. {@code feature.flag.search-typeahead: true}). Both
 * {@code FEATURE_FLAG_SEARCH_TYPEAHEAD} and {@code feature.flag.search-typeahead}
 * resolve to the same flag.
 */
public class EnvVarFeatureFlags implements FeatureFlags {

    static final String ENV_PREFIX = "FEATURE_FLAG_";
    static final String ENV_ROLLOUT_PREFIX = "FEATURE_FLAG_ROLLOUT_";
    static final String YAML_PREFIX = "feature.flag.";
    static final String YAML_ROLLOUT_PREFIX = "feature.flag.rollout.";

    private final Environment environment;

    public EnvVarFeatureFlags(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean isEnabled(String flagName) {
        if (!isValidFlagName(flagName)) {
            return false;
        }
        return readBooleanFlag(flagName);
    }

    @Override
    public boolean isEnabled(String flagName, String userId) {
        if (!isValidFlagName(flagName)) {
            return false;
        }
        if (readBooleanFlag(flagName)) {
            return true;
        }
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return resolveRolloutPercent(flagName)
                .map(percent -> userBucket(userId) < percent)
                .orElse(false);
    }

    private boolean readBooleanFlag(String flagName) {
        String value = readProperty(ENV_PREFIX + flagName, YAML_PREFIX + toYamlKey(flagName));
        return "true".equalsIgnoreCase(value);
    }

    private java.util.Optional<Integer> resolveRolloutPercent(String flagName) {
        String value = readProperty(
                ENV_ROLLOUT_PREFIX + flagName,
                YAML_ROLLOUT_PREFIX + toYamlKey(flagName));
        if (value == null || value.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            // Clamp out-of-range values so misconfiguration never causes a 500 — it
            // either turns the flag fully on or fully off, which are both safe outcomes.
            int clamped = Math.max(0, Math.min(100, parsed));
            return java.util.Optional.of(clamped);
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    /**
     * Read a property checking the env-style key first (UPPER_SNAKE_CASE) then
     * the YAML-style key (lowercase dotted). Spring's
     * {@code SystemEnvironmentPropertySource} also normalises across these.
     */
    private String readProperty(String envKey, String yamlKey) {
        String value = environment.getProperty(envKey);
        if (value != null) {
            return value;
        }
        return environment.getProperty(yamlKey);
    }

    /**
     * Translate {@code SEARCH_TYPEAHEAD} → {@code search-typeahead}.
     */
    private static String toYamlKey(String flagName) {
        return flagName.toLowerCase().replace('_', '-');
    }

    private static boolean isValidFlagName(String flagName) {
        return flagName != null && !flagName.isBlank();
    }

    /**
     * Map a user id to a stable bucket in {@code [0, 100)} using a 32-bit
     * Murmur3 hash. We use Murmur3 (not {@code String#hashCode}) for its
     * better avalanche behaviour — {@code "user-1"}, {@code "user-2"},
     * {@code "user-3"} land in genuinely different buckets, whereas
     * {@code hashCode} on short prefixes tends to cluster.
     */
    static int userBucket(String userId) {
        return Math.floorMod(murmur3(userId), 100);
    }

    /**
     * 32-bit Murmur3 implementation, ported from the Apache Commons / Guava
     * reference. Inlined here so common-library doesn't take a transitive
     * dependency on Guava just for one function.
     */
    static int murmur3(String input) {
        byte[] data = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int seed = 0;
        int length = data.length;
        int h1 = seed;
        int roundedEnd = (length & 0xfffffffc); // round down to 4-byte block

        for (int i = 0; i < roundedEnd; i += 4) {
            int k1 = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8)
                    | ((data[i + 2] & 0xff) << 16) | (data[i + 3] << 24);
            k1 *= 0xcc9e2d51;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= 0x1b873593;

            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        int k1 = 0;
        int tailStart = roundedEnd;
        int rem = length - roundedEnd;
        if (rem == 3) {
            k1 = (data[tailStart + 2] & 0xff) << 16;
        }
        if (rem >= 2) {
            k1 |= (data[tailStart + 1] & 0xff) << 8;
        }
        if (rem >= 1) {
            k1 |= (data[tailStart] & 0xff);
            k1 *= 0xcc9e2d51;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= 0x1b873593;
            h1 ^= k1;
        }

        h1 ^= length;
        h1 ^= (h1 >>> 16);
        h1 *= 0x85ebca6b;
        h1 ^= (h1 >>> 13);
        h1 *= 0xc2b2ae35;
        h1 ^= (h1 >>> 16);
        return h1;
    }
}
