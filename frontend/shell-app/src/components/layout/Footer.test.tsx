import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { Footer } from './Footer'
import theme from '../../theme'

const renderFooter = () => {
  return render(
    <BrowserRouter>
      <ThemeProvider theme={theme}>
        <Footer />
      </ThemeProvider>
    </BrowserRouter>
  )
}

describe('Footer', () => {
  it('should render copyright text', () => {
    renderFooter()
    expect(screen.getByText(/© 2025 E-Commerce Platform/i)).toBeInTheDocument()
  })

  it('should render customer service section', () => {
    renderFooter()
    expect(screen.getByText(/customer service/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /contact us/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /faq/i })).toBeInTheDocument()
  })

  it('should render company info section', () => {
    renderFooter()
    expect(screen.getByText(/company/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /about us/i })).toBeInTheDocument()
  })

  it('should render legal links section', () => {
    renderFooter()
    expect(screen.getByText(/legal/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /privacy policy/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /terms of service/i })).toBeInTheDocument()
  })

  it('should render social media icons', () => {
    renderFooter()
    expect(screen.getByTestId('facebook-icon')).toBeInTheDocument()
    expect(screen.getByTestId('twitter-icon')).toBeInTheDocument()
    expect(screen.getByTestId('instagram-icon')).toBeInTheDocument()
  })
})
