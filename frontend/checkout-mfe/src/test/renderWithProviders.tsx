import type { ReactElement, ReactNode } from 'react'
import { render, type RenderOptions } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import { MemoryRouter } from 'react-router-dom'
import CssBaseline from '@mui/material/CssBaseline'
import { theme } from '../theme/theme'

interface WrapperProps {
  children: ReactNode
}

interface RenderWithProvidersOptions extends Omit<RenderOptions, 'wrapper'> {
  initialEntries?: string[]
}

function createWrapper(initialEntries: string[] = ['/']) {
  return function Wrapper({ children }: WrapperProps) {
    return (
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <MemoryRouter initialEntries={initialEntries}>{children}</MemoryRouter>
      </ThemeProvider>
    )
  }
}

export function renderWithProviders(
  ui: ReactElement,
  { initialEntries = ['/'], ...options }: RenderWithProvidersOptions = {}
) {
  return render(ui, {
    wrapper: createWrapper(initialEntries),
    ...options,
  })
}

export { createWrapper }
