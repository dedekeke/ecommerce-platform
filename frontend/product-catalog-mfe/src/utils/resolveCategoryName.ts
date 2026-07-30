import type { Category } from '../types'

/**
 * Resolves a categoryId (slug or numeric id, as stored in productFilterStore/the URL) to the
 * category's display name — the value the search-service's `categories` facet filters on.
 */
export function resolveCategoryName(categories: Category[], categoryId: string | null): string | undefined {
  if (!categoryId) return undefined

  const queue: Category[] = [...categories]
  while (queue.length > 0) {
    const current = queue.shift() as Category
    if (current.id === categoryId || current.slug === categoryId) return current.name
    if (current.children?.length) queue.push(...current.children)
  }
  return undefined
}

export default resolveCategoryName
