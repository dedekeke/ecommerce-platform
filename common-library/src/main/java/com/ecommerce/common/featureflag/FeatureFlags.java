package com.ecommerce.common.featureflag;

/**
 * Minimal feature-flag SDK used across all backend services.
 *
 * <p>Backed by environment variables today (see {@link EnvVarFeatureFlags}).
 * The interface is intentionally kept tiny so we can swap to Unleash, LaunchDarkly,
 * or another provider without touching call-sites — see {@code docs/FEATURE_FLAGS.md}
 * for the migration plan.
 *
 * <p><strong>Naming convention.</strong> Flag names are {@code UPPER_SNAKE_CASE}.
 * Backend env var: {@code FEATURE_FLAG_<NAME>=true|false}.
 * Percentage rollout: {@code FEATURE_FLAG_ROLLOUT_<NAME>=0..100}.
 */
public interface FeatureFlags {

    /**
     * Whether the named flag is enabled globally.
     *
     * @param flagName flag name (UPPER_SNAKE_CASE, no {@code FEATURE_FLAG_} prefix)
     * @return {@code true} when {@code FEATURE_FLAG_<NAME>=true}, {@code false} otherwise
     */
    boolean isEnabled(String flagName);

    /**
     * Whether the named flag is enabled for a particular user.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>If {@code FEATURE_FLAG_<NAME>} is {@code true} → enabled.</li>
     *   <li>Else if {@code FEATURE_FLAG_ROLLOUT_<NAME>=N} is set → enabled when
     *       the user falls into the first {@code N%} of the hash space.</li>
     *   <li>Else → disabled.</li>
     * </ol>
     *
     * @param flagName flag name
     * @param userId   stable user identifier; {@code null}/blank disables rollout-based flags
     * @return whether the flag is enabled for this user
     */
    boolean isEnabled(String flagName, String userId);
}
