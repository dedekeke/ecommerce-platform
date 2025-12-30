import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import {
  PageSkeleton,
  ProductCardSkeleton,
  ProductListSkeleton,
} from './LoadingSkeleton'
import theme from '../../theme'

const renderWithTheme = (component: React.ReactNode) => {
  return render(<ThemeProvider theme={theme}>{component}</ThemeProvider>)
}

describe('PageSkeleton', () => {
  it('should render skeleton elements', () => {
    renderWithTheme(<PageSkeleton />)
    expect(screen.getByTestId('page-skeleton')).toBeInTheDocument()
  })

  it('should have proper accessibility attributes', () => {
    renderWithTheme(<PageSkeleton />)
    expect(screen.getByTestId('page-skeleton')).toHaveAttribute(
      'aria-label',
      'Loading content'
    )
  })
})

describe('ProductCardSkeleton', () => {
  it('should render product card skeleton', () => {
    renderWithTheme(<ProductCardSkeleton />)
    expect(screen.getByTestId('product-card-skeleton')).toBeInTheDocument()
  })
})

describe('ProductListSkeleton', () => {
  it('should render multiple product card skeletons', () => {
    renderWithTheme(<ProductListSkeleton count={4} />)
    const skeletons = screen.getAllByTestId('product-card-skeleton')
    expect(skeletons).toHaveLength(4)
  })

  it('should render default count of 6 skeletons', () => {
    renderWithTheme(<ProductListSkeleton />)
    const skeletons = screen.getAllByTestId('product-card-skeleton')
    expect(skeletons).toHaveLength(6)
  })
})
