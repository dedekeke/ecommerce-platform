# Feature Flags

This platform uses an **in-house, environment-variable-driven** feature-flag
SDK. It is intentionally minimal — boolean toggles plus deterministic
percentage rollouts — and lives in the `common-library` so every backend
service picks it up via Spring Boot autoconfiguration.

[← Back to README](../README.md)

## Why not Unleash / LaunchDarkly?

External providers add a third-party dependency, a network call on every
flag evaluation (or a polling SDK), and an account to manage. We don't have
the operational complexity yet to justify that. The trade-off: flag changes
require a redeploy (backend) or rebuild (frontend).

**We will migrate to a real provider when** any of these triggers fires:

1. The number of *active* flags crosses **10**. Below that, env vars are
   manageable; above that, the SDK overhead pays for itself.
2. We need **dynamic rollout** without a deploy (e.g. emergency kill-switch
   for a cascading bug).
3. We start running **A/B experiments** that need flag-segmented analytics.

The interface (`com.ecommerce.common.featureflag.FeatureFlags`) is small on
purpose so the migration is a one-line autoconfiguration swap.

## Naming convention

Flags are `UPPER_SNAKE_CASE`. The SDK strips/adds the prefix automatically:

| Surface       | Variable name                          | Example                                  |
|---------------|----------------------------------------|------------------------------------------|
| Backend env   | `FEATURE_FLAG_<NAME>=true|false`       | `FEATURE_FLAG_SEARCH_TYPEAHEAD=true`     |
| Backend YAML  | `feature.flag.<lower-kebab>: true`     | `feature.flag.search-typeahead: true`    |
| Backend roll. | `FEATURE_FLAG_ROLLOUT_<NAME>=0..100`   | `FEATURE_FLAG_ROLLOUT_NEW_PRICING=10`    |
| Frontend env  | `VITE_FEATURE_FLAG_<NAME>=true|false`  | `VITE_FEATURE_FLAG_RECOMMENDATIONS=true` |

The same `<NAME>` segment identifies the flag everywhere — a backend flag
named `RECOMMENDATIONS` and a frontend flag named `RECOMMENDATIONS` are
*conceptually* the same flag, even though they're separate env vars.

## Adding a flag

### Backend

```java
import com.ecommerce.common.featureflag.FeatureFlags;

@Service
@RequiredArgsConstructor
public class CheckoutService {
    private final FeatureFlags featureFlags;

    public Receipt checkout(CheckoutCommand cmd) {
        if (featureFlags.isEnabled("NEW_CHECKOUT_FLOW", cmd.userId())) {
            return newFlow(cmd);
        }
        return legacyFlow(cmd);
    }
}
```

`FeatureFlags` is auto-wired by `FeatureFlagAutoConfiguration` — just inject
it. No `@EnableFeatureFlags` annotation needed.

To enable the flag:

```bash
# Option A: env var (12-factor friendly)
export FEATURE_FLAG_NEW_CHECKOUT_FLOW=true

# Option B: YAML (per-environment overrides)
# application-staging.yml
feature:
  flag:
    new-checkout-flow: true
```

### Frontend

```tsx
import { useFeatureFlag } from './featureFlags'

export function CheckoutPage() {
  const newFlow = useFeatureFlag('NEW_CHECKOUT_FLOW')
  return newFlow ? <NewCheckout /> : <LegacyCheckout />
}
```

`.env`:

```
VITE_FEATURE_FLAG_NEW_CHECKOUT_FLOW=true
```

Vite replaces `import.meta.env.VITE_FEATURE_FLAG_*` references at build
time, so flipping the flag requires a `npm run build` + redeploy.

## Rollout strategies

### Boolean (on/off)

The simplest case. Set `FEATURE_FLAG_<NAME>=true` and the flag is enabled
for every caller. Use this for kill-switches and per-environment toggles.

### Percentage rollout

Set `FEATURE_FLAG_ROLLOUT_<NAME>=N` (where `N` is 0–100). Each `userId` is
hashed via Murmur3 and bucketed into `[0, 100)`; users land in the "enabled"
bucket if their hash mod 100 is strictly less than `N`.

```java
// 10% of users see the new pricing logic
// FEATURE_FLAG_ROLLOUT_NEW_PRICING=10
boolean enabled = featureFlags.isEnabled("NEW_PRICING", userId);
```

Properties:

* **Deterministic** — same user always gets the same answer (until you
  change the flag value or rollout percentage).
* **Well-distributed** — Murmur3 avoids the clustering you'd get from
  `String#hashCode()` on sequential IDs.
* **Fail-closed** — calling `isEnabled(name, null)` returns `false` because
  null userIds can't be bucketed deterministically.

A boolean toggle (`FEATURE_FLAG_<NAME>=true`) **always wins** over a
percentage rollout. Set the boolean once you're ready to graduate the flag
to 100%.

## Live flags

Tracked here so we don't lose visibility:

| Flag                          | Surface | Default | Owner / context                             |
|-------------------------------|---------|---------|---------------------------------------------|
| `SEARCH_TYPEAHEAD`            | backend | `false` | Search §3.11. Enabled in dev/staging only. |
| `RECOMMENDATIONS`             | frontend| `false` | Catalog MFE. Gates rec strip on detail pg. |

Update this table when adding a new flag.

## Testing

* **Backend** — `FeatureFlags` is an interface; tests instantiate
  `EnvVarFeatureFlags` against a Spring `MockEnvironment` to set flags
  without touching the process environment. See
  `common-library/src/test/java/com/ecommerce/common/featureflag/EnvVarFeatureFlagsTest.java`.
* **Frontend** — use `vi.stubEnv('VITE_FEATURE_FLAG_<NAME>', 'true')` and
  call `vi.unstubAllEnvs()` in `afterEach`. See
  `frontend/shell-app/src/featureFlags.test.ts`.

## When to retire a flag

* The feature has been at 100% rollout in production for **2 sprints** with
  no rollback.
* The legacy code path is no longer reachable.
* Delete the flag, the legacy path, and the entry in the "Live flags" table
  above. Stale flags accumulate technical debt — they're more dangerous than
  visible bugs because nobody knows whether the off-path still works.

## Migration to Unleash (future)

When we cross the trigger thresholds above:

1. Add the Unleash Spring Boot starter to `common-library`.
2. Provide a new `UnleashFeatureFlags` implementation of `FeatureFlags`.
3. Register it as a bean — `EnvVarFeatureFlags` will back off via
   `@ConditionalOnMissingBean`.
4. Remove the env-var docs from this file (keep the interface docs).
5. Mirror the change on the frontend with `unleash-proxy-client-react`.

No call-site changes required.

## See also

* `common-library/src/main/java/com/ecommerce/common/featureflag/` — SDK source
* `frontend/shell-app/src/featureFlags.ts` — frontend SDK source
* `docs/SCALING_AND_IMPROVEMENTS.md` — broader roadmap context
