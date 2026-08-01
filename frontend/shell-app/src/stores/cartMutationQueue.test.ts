import { describe, it, expect } from 'vitest'
import { createMutationQueue } from './cartMutationQueue'

function deferred() {
  let resolve!: () => void
  let reject!: (err: unknown) => void
  const promise = new Promise<void>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('createMutationQueue', () => {
  describe('enqueue', () => {
    it('should serialize tasks per key — the second starts only after the first settles', async () => {
      const queue = createMutationQueue<undefined>()
      const first = deferred()
      const order: string[] = []

      const p1 = queue.enqueue('k', async () => {
        order.push('first:start')
        await first.promise
        order.push('first:end')
      })
      const p2 = queue.enqueue('k', async () => {
        order.push('second:start')
      })

      await Promise.resolve()
      expect(order).toEqual(['first:start'])

      first.resolve()
      await Promise.all([p1, p2])
      expect(order).toEqual(['first:start', 'first:end', 'second:start'])
    })

    it('should still run the next task after a rejected predecessor', async () => {
      const queue = createMutationQueue<undefined>()
      const ran: string[] = []

      const p1 = queue.enqueue('k', async () => {
        throw new Error('boom')
      })
      const p2 = queue.enqueue('k', async () => {
        ran.push('second')
      })

      await expect(p1).rejects.toThrow('boom')
      await p2
      expect(ran).toEqual(['second'])
    })

    it('should not serialize across different keys', async () => {
      const queue = createMutationQueue<undefined>()
      const first = deferred()
      const order: string[] = []

      void queue.enqueue('a', async () => {
        await first.promise
        order.push('a')
      })
      const pB = queue.enqueue('b', async () => {
        order.push('b')
      })

      await pB
      expect(order).toEqual(['b'])
      first.resolve()
    })
  })

  describe('begin / staleness', () => {
    it('should stale an earlier mutation when a newer one begins on the same key', () => {
      const queue = createMutationQueue<undefined>()
      const guardA = queue.begin('k')
      expect(guardA.isStale()).toBe(false)

      const guardB = queue.begin('k')
      expect(guardA.isStale()).toBe(true)
      expect(guardB.isStale()).toBe(false)
    })

    it('should not stale mutations on other keys', () => {
      const queue = createMutationQueue<undefined>()
      const guardA = queue.begin('a')
      queue.begin('b')
      expect(guardA.isStale()).toBe(false)
    })
  })

  describe('baselines', () => {
    it('should keep the FIRST baseline of a burst and consume it on take', () => {
      const queue = createMutationQueue<number>()
      queue.rememberBaseline('k', 1)
      queue.rememberBaseline('k', 2)

      expect(queue.takeBaseline('k')).toEqual({ has: true, value: 1 })
      expect(queue.takeBaseline('k')).toEqual({ has: false })
    })

    it('should drop the baseline on commit', () => {
      const queue = createMutationQueue<number>()
      queue.rememberBaseline('k', 1)
      queue.commitBaseline('k')
      expect(queue.takeBaseline('k')).toEqual({ has: false })
    })

    it('should support undefined as a legitimate baseline value (item absent before)', () => {
      const queue = createMutationQueue<number | undefined>()
      queue.rememberBaseline('k', undefined)
      expect(queue.takeBaseline('k')).toEqual({ has: true, value: undefined })
    })
  })

  describe('invalidateAll', () => {
    it('should stale every begun mutation and drop baselines', () => {
      const queue = createMutationQueue<number>()
      const guardA = queue.begin('a')
      const guardB = queue.begin('b')
      queue.rememberBaseline('a', 1)

      queue.invalidateAll()

      expect(guardA.isStale()).toBe(true)
      expect(guardB.isStale()).toBe(true)
      expect(queue.takeBaseline('a')).toEqual({ has: false })
    })
  })
})
