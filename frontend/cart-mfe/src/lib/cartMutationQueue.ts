/**
 * Per-key optimistic-mutation coordination primitive. An identical copy lives in
 * shell-app (src/stores/cartMutationQueue.ts) — collapsing the duplication into
 * shared-ui is the phase-B refactor.
 *
 * Guards against out-of-order optimistic writes:
 *  - `enqueue` SERIALIZES tasks per key, so two requests for the same item never
 *    overlap in flight.
 *  - `begin` hands each mutation a generation guard; a superseded (stale) mutation
 *    skips issuing its request entirely (coalescing rapid edits into one request
 *    carrying the final target) and must never reconcile or roll back.
 *  - Baselines remember the last server-confirmed state once per mutation burst, so
 *    a failing latest mutation rolls back to truth instead of an intermediate
 *    optimistic state.
 */
export interface MutationGuard {
  isStale: () => boolean
}

export function createMutationQueue<S>() {
  const tails = new Map<string, Promise<void>>()
  const generations = new Map<string, number>()
  const baselines = new Map<string, S>()

  /** Marks a new mutation for `key`; any previously begun mutation becomes stale. */
  const begin = (key: string): MutationGuard => {
    const gen = (generations.get(key) ?? 0) + 1
    generations.set(key, gen)
    return { isStale: () => generations.get(key) !== gen }
  }

  /** Runs `task` after every in-flight/queued task for `key` settles (even rejected ones). */
  const enqueue = (key: string, task: () => Promise<void>): Promise<void> => {
    const tail = tails.get(key) ?? Promise.resolve()
    const next = tail.then(task, task)
    tails.set(
      key,
      next.then(
        () => undefined,
        () => undefined
      )
    )
    return next
  }

  /** Records the pre-mutation state once per burst; later mutations keep the first baseline. */
  const rememberBaseline = (key: string, value: S): void => {
    if (!baselines.has(key)) baselines.set(key, value)
  }

  /** The server confirmed the current mutation: the optimistic state IS the truth now. */
  const commitBaseline = (key: string): void => {
    baselines.delete(key)
  }

  /** Consumes the baseline for rollback; `has: false` when no burst was pending. */
  const takeBaseline = (key: string): { has: boolean; value?: S } => {
    if (!baselines.has(key)) return { has: false }
    const value = baselines.get(key) as S
    baselines.delete(key)
    return { has: true, value }
  }

  /** Stales every begun mutation and drops all baselines (e.g. the cart was cleared wholesale). */
  const invalidateAll = (): void => {
    for (const [key, gen] of generations) generations.set(key, gen + 1)
    baselines.clear()
  }

  const reset = (): void => {
    tails.clear()
    generations.clear()
    baselines.clear()
  }

  return { begin, enqueue, rememberBaseline, commitBaseline, takeBaseline, invalidateAll, reset }
}

export type MutationQueue<S> = ReturnType<typeof createMutationQueue<S>>
