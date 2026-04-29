import { http, HttpResponse } from 'msw'
import { mockProducts, mockCategories, mockPaginatedProducts } from './products'

const API_BASE = 'http://localhost:8080/api'

export const handlers = [
  http.get(`${API_BASE}/products`, ({ request }) => {
    const url = new URL(request.url)
    const page = parseInt(url.searchParams.get('page') || '0')
    const size = parseInt(url.searchParams.get('size') || '12')
    const search = url.searchParams.get('search')
    const categoryId = url.searchParams.get('categoryId')
    const minPrice = url.searchParams.get('minPrice')
    const maxPrice = url.searchParams.get('maxPrice')

    let filtered = [...mockProducts]

    if (search) {
      const searchLower = search.toLowerCase()
      filtered = filtered.filter(
        (p) =>
          p.name.toLowerCase().includes(searchLower) ||
          p.description.toLowerCase().includes(searchLower)
      )
    }

    if (categoryId) {
      filtered = filtered.filter((p) => p.category?.id === categoryId)
    }

    if (minPrice) {
      filtered = filtered.filter((p) => p.price >= parseFloat(minPrice))
    }

    if (maxPrice) {
      filtered = filtered.filter((p) => p.price <= parseFloat(maxPrice))
    }

    const start = page * size
    const paged = filtered.slice(start, start + size)

    return HttpResponse.json({
      content: paged,
      page,
      size,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / size),
      first: page === 0,
      last: page >= Math.ceil(filtered.length / size) - 1,
    })
  }),

  http.get(`${API_BASE}/products/featured`, () => {
    return HttpResponse.json(mockProducts.slice(0, 4))
  }),

  http.get(`${API_BASE}/products/:id`, ({ params }) => {
    const { id } = params
    const product = mockProducts.find((p) => p.id === id)

    if (!product) {
      return HttpResponse.json({ message: 'Product not found' }, { status: 404 })
    }

    return HttpResponse.json(product)
  }),

  http.get(`${API_BASE}/v1/categories`, () => {
    return HttpResponse.json(mockCategories)
  }),

  http.get(`${API_BASE}/v1/categories/root`, () => {
    return HttpResponse.json(mockCategories.filter((c) => c.level === 0))
  }),

  http.get(`${API_BASE}/v1/categories/:id`, ({ params }) => {
    const { id } = params
    const category = mockCategories.find((c) => c.id === id)

    if (!category) {
      return HttpResponse.json({ message: 'Category not found' }, { status: 404 })
    }

    return HttpResponse.json(category)
  }),

  http.get(`${API_BASE}/v1/categories/slug/:slug`, ({ params }) => {
    const { slug } = params
    const category = mockCategories.find((c) => c.slug === slug)

    if (!category) {
      return HttpResponse.json({ message: 'Category not found' }, { status: 404 })
    }

    return HttpResponse.json(category)
  }),
]

export { mockProducts, mockCategories, mockPaginatedProducts }
