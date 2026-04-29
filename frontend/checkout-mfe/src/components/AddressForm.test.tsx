import { describe, it, expect, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../test/renderWithProviders'
import AddressForm from './AddressForm'

const fillForm = async (overrides: Record<string, string> = {}) => {
  const fields: Record<string, string> = {
    'Full name': 'Jane Doe',
    'Address line 1': '123 Main St',
    'Address line 2': 'Apt 4',
    City: 'San Francisco',
    State: 'CA',
    'Postal code': '94105',
    ...overrides,
  }
  for (const [label, value] of Object.entries(fields)) {
    const input = screen.getByLabelText(new RegExp(label, 'i'))
    await userEvent.clear(input)
    await userEvent.type(input, value)
  }
}

describe('AddressForm', () => {
  it('should render all required fields', () => {
    renderWithProviders(<AddressForm onValid={vi.fn()} onChange={vi.fn()} />)
    expect(screen.getByLabelText(/full name/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/address line 1/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/city/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/state/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/postal code/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/country/i)).toBeInTheDocument()
  })

  it('should default country to US', () => {
    renderWithProviders(<AddressForm onValid={vi.fn()} onChange={vi.fn()} />)
    const countryInput = screen.getByLabelText(/country/i) as HTMLInputElement
    expect(countryInput.value).toBe('US')
  })

  it('should show validation errors when required fields are empty and touched', async () => {
    renderWithProviders(<AddressForm onValid={vi.fn()} onChange={vi.fn()} />)
    const fullNameInput = screen.getByLabelText(/full name/i)
    await userEvent.click(fullNameInput)
    await userEvent.tab()
    await waitFor(() => {
      expect(screen.getByText(/full name is required/i)).toBeInTheDocument()
    })
  })

  it('should show error for empty address line 1', async () => {
    renderWithProviders(<AddressForm onValid={vi.fn()} onChange={vi.fn()} />)
    const line1 = screen.getByLabelText(/address line 1/i)
    await userEvent.click(line1)
    await userEvent.tab()
    await waitFor(() => {
      expect(screen.getByText(/address line 1 is required/i)).toBeInTheDocument()
    })
  })

  it('should call onValid with the address when all required fields are filled', async () => {
    const onValid = vi.fn()
    renderWithProviders(<AddressForm onValid={onValid} onChange={vi.fn()} />)
    await fillForm()
    await waitFor(() => {
      expect(onValid).toHaveBeenCalledWith(
        expect.objectContaining({
          fullName: 'Jane Doe',
          line1: '123 Main St',
          city: 'San Francisco',
          state: 'CA',
          postalCode: '94105',
          country: 'US',
        })
      )
    })
  })

  it('should call onChange whenever a field changes', async () => {
    const onChange = vi.fn()
    renderWithProviders(<AddressForm onValid={vi.fn()} onChange={onChange} />)
    await userEvent.type(screen.getByLabelText(/full name/i), 'J')
    expect(onChange).toHaveBeenCalled()
  })

  it('should pre-populate fields from initialValues prop', () => {
    const initial = {
      fullName: 'Pre Filled',
      line1: '999 Oak Ave',
      line2: '',
      city: 'Austin',
      state: 'TX',
      postalCode: '73301',
      country: 'US',
    }
    renderWithProviders(<AddressForm onValid={vi.fn()} onChange={vi.fn()} initialValues={initial} />)
    expect((screen.getByLabelText(/full name/i) as HTMLInputElement).value).toBe('Pre Filled')
    expect((screen.getByLabelText(/city/i) as HTMLInputElement).value).toBe('Austin')
  })

  it('should show error when city is missing', async () => {
    renderWithProviders(<AddressForm onValid={vi.fn()} onChange={vi.fn()} />)
    const city = screen.getByLabelText(/city/i)
    await userEvent.click(city)
    await userEvent.tab()
    await waitFor(() => {
      expect(screen.getByText(/city is required/i)).toBeInTheDocument()
    })
  })
})
