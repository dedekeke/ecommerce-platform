import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { NotFound } from './NotFound'
import { lightTheme } from '../../theme'

const renderNotFound = () =>
  render(
    <BrowserRouter>
      <ThemeProvider theme={lightTheme}>
        <NotFound />
      </ThemeProvider>
    </BrowserRouter>
  )

describe('NotFound', () => {
  it('should render a main landmark', () => {
    renderNotFound()
    expect(screen.getByRole('main')).toBeInTheDocument()
  })

  it('should render the headline', () => {
    renderNotFound()
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument()
  })

  it('should render the descriptive message', () => {
    renderNotFound()
    expect(screen.getByText(/this page went on vacation/i)).toBeInTheDocument()
  })

  it('should render a "Go home" link to /', () => {
    renderNotFound()
    const homeLink = screen.getByRole('link', { name: /go home/i })
    expect(homeLink).toBeInTheDocument()
    expect(homeLink).toHaveAttribute('href', '/')
  })

  it('should render a "Browse products" link to /products', () => {
    renderNotFound()
    const productsLink = screen.getByRole('link', { name: /browse products/i })
    expect(productsLink).toBeInTheDocument()
    expect(productsLink).toHaveAttribute('href', '/products')
  })

  it('should render the illustration as aria-hidden', () => {
    renderNotFound()
    const svg = document.querySelector('svg')
    expect(svg).toHaveAttribute('aria-hidden', 'true')
  })
})
