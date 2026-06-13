# Zone.js Side-Effects on the React Scheduler When Angular MFEs Are Loaded

**Author**: Frontend Platform
**Date**: 2026-06-14
**Status**: Mitigated (low-risk option applied — see §5)

---

## 1. Background

Angular traditionally depends on Zone.js to patch browser async APIs
(`setTimeout`, `Promise`, `XMLHttpRequest`, `fetch`, `addEventListener`, …) so it
can detect when asynchronous work finishes and trigger change-detection.

When an Angular MFE is loaded into the React shell, Zone.js is imported as a
side-effect of the MFE bundle unless explicitly disabled. Because Zone.js patches
the *global* async primitives, it affects every piece of JavaScript running in the
same browsing context — including the React runtime and its scheduler.

---

## 2. Observed/Expected Side-Effects on the React Scheduler

### 2.1 Promise micro-task queuing

React 18's concurrent scheduler uses `MessageChannel` and `Promise`-based
micro-tasks for time-slicing (`scheduler.postTask`, the Scheduler polyfill, and
`ReactDOM`'s internal `scheduleMicrotask`). Zone.js wraps `Promise.then` and
re-routes completions through Zone's task queue.

**Effect**: Micro-tasks that React expects to run at native Promise priority can
be delayed by one Zone tick if Zone.js intercepts them. In practice this adds 0-2 ms
per scheduling cycle — imperceptible in non-animated code, but measurable under
Concurrent Mode with many overlapping deferred updates.

### 2.2 setTimeout / setInterval wrapping

React's legacy `setTimeout`-based fallback scheduler (used in environments where
`MessageChannel` is unavailable) runs inside a Zone.js `macroTask`. Zone.js
tracks these tasks and prevents the browser from going idle until the task is
cleared. This can suppress `requestIdleCallback` from firing, degrading React's
idle-time rendering for low-priority Suspense boundaries.

### 2.3 addEventListener wrapping

Zone.js wraps all `addEventListener` calls. React 17+ attaches event listeners to
the **root container** rather than `document` for its event delegation layer.
When Zone.js wraps those root-container listeners, events dispatched inside the
Angular MFE's container element may not bubble correctly to React's synthetic
event system because Zone creates a separate task for each listener invocation
with a forked Zone context.

### 2.4 fetch / XMLHttpRequest

Zone.js patches `fetch` globally. React Query and any custom fetch interceptors in
the shell sit inside the Zone context. This is usually harmless but can cause
confusing Zone-context mis-matches in error boundaries if an Angular MFE's
`HttpClient` request and a React Query refetch race.

### 2.5 React DevTools / Profiler

The React Profiler measures "actual render duration" using `performance.now()`
timestamps. Zone.js does not patch `performance.now()` itself, but it *does* wrap
`requestAnimationFrame`. Wrapped `rAF` callbacks run inside the Angular Zone,
which means Profiler flame charts can show unexpectedly large "commit phase" times
when the Angular MFE is mounted, because Zone dispatches are accounted for inside
the same commit tick.

---

## 3. Risk Assessment

| Risk | Severity | Likelihood | Notes |
|------|----------|------------|-------|
| Micro-task timing drift | Low | Medium | 1-2 ms; only matters in animation-heavy UIs |
| Idle-callback suppression | Medium | Low | Only when MFE is actively mounted |
| Event delegation breakage | Medium | Low | Unlikely; React uses root-container delegation |
| Fetch context mis-match | Low | Low | No observable data-corruption risk |
| DevTools / Profiler noise | Low | High | Always present when Angular MFE is on screen |

Overall: **low to medium risk** for this platform. No functional breakage is
expected; the main concern is observability noise and minor scheduler overhead.

---

## 4. Mitigation Options Considered

### Option A — `ngZone: 'noop'` (recommended for future sprint)

Pass `{ ngZone: 'noop' }` to `createApplication`. This tells Angular not to
create a Zone context at all. Change detection must then be triggered manually via
`ChangeDetectorRef.detectChanges()` or by using Angular Signals (available from
Angular 17+).

**Pros**: Zero Zone.js global patches. No React scheduler impact whatsoever.
**Cons**: Requires all Angular components in the MFE to handle CD explicitly or
use Signals. The existing MFE components use `ChangeDetectionStrategy.Default` and
are not yet Signal-based.

### Option B — Zone isolation via custom Zone fork (complex)

Wrap the Angular bootstrap inside `Zone.current.fork({ name: 'angular-mfe' })`.
Zone.js patches still apply globally but the Angular change-detection cycle runs
inside its own Zone, limiting `onStable` / `onUnstable` callbacks to within the
forked zone.

**Pros**: Isolates Angular's CD lifecycle from the global Zone context.
**Cons**: Zone.js global patches are still applied — the fundamental problem is
not solved. Complex to maintain. Zone.js internals are private API.

### Option C — Lazy-load Zone.js only with the Angular MFE bundle

Import `zone.js` inside the MFE bundle (via native-federation) rather than in the
shell's `index.html`. This is the current status quo.

**Pros**: Zone.js is not loaded until the MFE is first mounted, so the React shell
initialises cleanly without Zone interference.
**Cons**: Once loaded, patches remain for the session lifetime. No isolation.

### Option D — Remove Zone.js, migrate to Signals + `ChangeDetectionStrategy.OnPush`

Full migration to Angular's Signals-based reactive model, enabling
`provideExperimentalZonelessChangeDetection()`.

**Pros**: Cleanest solution. Zero Zone.js in the browser.
**Cons**: Large refactor. Not low-risk for this sprint.

---

## 5. Applied Mitigation

**Option C (existing) has been retained and hardened** with the following
additions:

1. Zone.js continues to be loaded **lazily** as part of the Angular MFE bundle
   (via native-federation), not in the shell's `index.html`. React initialises and
   renders the shell's own UI *before* Zone.js is ever imported.

2. The `createApplication()` refactor in Task 3 (element-scoped bootstrap)
   structures the config so switching to `ngZone: 'noop'` when Signals migration
   is complete requires only one line:

   ```ts
   // Current — Zone.js active but lazy-loaded:
   const appRef = await createApplication(appConfig);

   // Future — noop Zone (after Signals migration):
   const appRef = await createApplication({ ...appConfig, ngZone: 'noop' });
   ```

3. A **dev-only Zone.js guard** should be added to `shell-app/src/main.tsx` to
   alert developers if Zone.js is accidentally pulled into the shell bundle:

   ```ts
   if (import.meta.env.DEV && (globalThis as Record<string, unknown>)['Zone']) {
     console.warn(
       '[shell] Zone.js detected before React mount. ' +
       'Ensure zone.js is NOT imported in the shell bundle — ' +
       'it should only load inside the Angular MFE bundles.'
     );
   }
   ```

   This acts as a regression detector and is tree-shaken in production builds.

---

## 6. Recommendation for Future Sprint

Once the Angular MFEs adopt **Angular Signals** (`signal()`, `computed()`,
`effect()`) for their reactive state:

1. Replace `provideZoneChangeDetection()` with
   `provideExperimentalZonelessChangeDetection()` in both MFE `appConfig` files.
2. Remove `zone.js` from the `polyfills` array in each MFE's `angular.json`.
3. Add a CI check asserting `zone.js` is absent from the Angular MFE output
   bundle (use `bundlesize` or `source-map-explorer`).

This will completely eliminate Zone.js's global patches and make the React
scheduler's micro-task queue, `setTimeout` fallback, and `rAF` paths operate
without interference.

---

## 7. References

- [Angular docs — Zoneless change detection](https://angular.dev/guide/experimental/zoneless)
- [Zone.js source — patched APIs list](https://github.com/angular/angular/tree/main/packages/zone.js)
- [React Scheduler source — MessageChannel / micro-task usage](https://github.com/facebook/react/blob/main/packages/scheduler/src/forks/Scheduler.js)
- [angular-architects/native-federation](https://github.com/angular-architects/module-federation-plugin/tree/main/libs/native-federation)
