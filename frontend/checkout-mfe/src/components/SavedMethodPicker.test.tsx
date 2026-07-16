import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../test/renderWithProviders'
import type { SavedPaymentMethod } from '../api/types'

const listSavedMethods = vi.fn()
vi.mock('../api/paymentMethodsService', () => ({
  listSavedMethods: (...args: unknown[]) => listSavedMethods(...args),
}))

import SavedMethodPicker from './SavedMethodPicker'

const visa: SavedPaymentMethod = {
  id: 1,
  userId: 'user-1',
  provider: 'stripe',
  providerId: 'pm_visa',
  last4: '4242',
  brand: 'visa',
  expMonth: 12,
  expYear: 2027,
  isDefault: true,
  createdAt: '2026-01-01T00:00:00.000Z',
}

const mastercard: SavedPaymentMethod = {
  id: 2,
  userId: 'user-1',
  provider: 'stripe',
  providerId: 'pm_mc',
  last4: '5555',
  brand: 'mastercard',
  expMonth: 6,
  expYear: 2028,
  isDefault: false,
  createdAt: '2026-02-01T00:00:00.000Z',
}

describe('SavedMethodPicker', () => {
  beforeEach(() => {
    listSavedMethods.mockReset()
  })

  it('should show a loading skeleton while fetching', () => {
    listSavedMethods.mockReturnValue(new Promise(() => {}))
    renderWithProviders(<SavedMethodPicker userId="user-1" onSelectionChange={vi.fn()} />)
    expect(screen.getByLabelText(/loading saved payment methods/i)).toBeInTheDocument()
  })

  it('should render each saved method plus a "use a new card" option', async () => {
    listSavedMethods.mockResolvedValue([visa, mastercard])
    renderWithProviders(<SavedMethodPicker userId="user-1" onSelectionChange={vi.fn()} />)

    await screen.findByRole('radio', { name: /visa.*4242.*12\/2027/i })
    expect(screen.getByRole('radio', { name: /mastercard.*5555.*06\/2028/i })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /use a new card/i })).toBeInTheDocument()
    expect(screen.getByText('Default')).toBeInTheDocument()
  })

  it('should preselect the default method and notify the caller', async () => {
    listSavedMethods.mockResolvedValue([mastercard, visa])
    const onSelectionChange = vi.fn()
    renderWithProviders(<SavedMethodPicker userId="user-1" onSelectionChange={onSelectionChange} />)

    await waitFor(() =>
      expect(onSelectionChange).toHaveBeenCalledWith({ type: 'saved', method: visa })
    )
    const radio = screen.getByRole('radio', { name: /visa.*4242/i })
    expect(radio).toBeChecked()
  })

  it('should call onSelectionChange when the shopper switches to a different saved method', async () => {
    listSavedMethods.mockResolvedValue([visa, mastercard])
    const onSelectionChange = vi.fn()
    renderWithProviders(<SavedMethodPicker userId="user-1" onSelectionChange={onSelectionChange} />)

    await screen.findByRole('radio', { name: /mastercard.*5555/i })
    await userEvent.click(screen.getByRole('radio', { name: /mastercard.*5555/i }))

    expect(onSelectionChange).toHaveBeenCalledWith({ type: 'saved', method: mastercard })
  })

  it('should call onSelectionChange with "new" when "Use a new card" is selected', async () => {
    listSavedMethods.mockResolvedValue([visa])
    const onSelectionChange = vi.fn()
    renderWithProviders(<SavedMethodPicker userId="user-1" onSelectionChange={onSelectionChange} />)

    await screen.findByRole('radio', { name: /visa.*4242/i })
    await userEvent.click(screen.getByRole('radio', { name: /use a new card/i }))

    expect(onSelectionChange).toHaveBeenCalledWith({ type: 'new' })
  })

  it('should render nothing and fall through to "new" when there are no saved methods', async () => {
    listSavedMethods.mockResolvedValue([])
    const onSelectionChange = vi.fn()
    const { container } = renderWithProviders(
      <SavedMethodPicker userId="user-1" onSelectionChange={onSelectionChange} />
    )

    await waitFor(() => expect(onSelectionChange).toHaveBeenCalledWith({ type: 'new' }))
    expect(container).toBeEmptyDOMElement()
  })

  it('should show an error alert and fall through to "new" when the fetch fails', async () => {
    listSavedMethods.mockRejectedValue(new Error('boom'))
    const onSelectionChange = vi.fn()
    renderWithProviders(<SavedMethodPicker userId="user-1" onSelectionChange={onSelectionChange} />)

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/could not load/i))
    expect(onSelectionChange).toHaveBeenCalledWith({ type: 'new' })
  })
})
