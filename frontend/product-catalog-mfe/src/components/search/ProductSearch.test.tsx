import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '../../test/renderWithProviders'
import { ProductSearch } from './ProductSearch'
import { searchService } from '../../api'

vi.mock('../../api', () => ({
  searchService: {
    autocomplete: vi.fn(),
  },
}))

const mockAutocomplete = vi.mocked(searchService.autocomplete)

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

describe('ProductSearch', () => {
  beforeEach(() => {
    mockAutocomplete.mockResolvedValue([])
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('should render a search box with an accessible name', () => {
    renderWithProviders(<ProductSearch onSearch={vi.fn()} />)
    expect(screen.getByRole('combobox', { name: /search products/i })).toBeInTheDocument()
  })

  it('should pre-fill the input from initialQuery', () => {
    renderWithProviders(<ProductSearch onSearch={vi.fn()} initialQuery="shoes" />)
    expect(screen.getByRole('combobox')).toHaveValue('shoes')
  })

  it('should call onSearch with the trimmed query on Enter', async () => {
    const onSearch = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<ProductSearch onSearch={onSearch} />)

    const input = screen.getByRole('combobox')
    await user.type(input, '  sneakers  ')
    await user.keyboard('{Enter}')

    expect(onSearch).toHaveBeenCalledWith('sneakers')
  })

  it('should call onSearch on form submit (search button / native submit)', async () => {
    const onSearch = vi.fn()
    renderWithProviders(<ProductSearch onSearch={onSearch} initialQuery="jackets" />)

    const form = screen.getByRole('search')
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))

    expect(onSearch).toHaveBeenCalledWith('jackets')
  })

  it('should fetch and show autocomplete suggestions after the debounce delay', async () => {
    mockAutocomplete.mockResolvedValueOnce(['red shoes', 'running shoes'])
    const user = userEvent.setup()
    renderWithProviders(<ProductSearch onSearch={vi.fn()} />)

    await user.type(screen.getByRole('combobox'), 'sho')

    expect(await screen.findByRole('option', { name: 'red shoes' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'running shoes' })).toBeInTheDocument()
    expect(mockAutocomplete).toHaveBeenCalledWith('sho')
  }, 10000)

  it('should navigate suggestions with the keyboard and select one with Enter', async () => {
    mockAutocomplete.mockResolvedValueOnce(['red shoes', 'running shoes'])
    const onSearch = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<ProductSearch onSearch={onSearch} />)

    const input = screen.getByRole('combobox')
    await user.type(input, 'sho')
    await screen.findByRole('option', { name: 'red shoes' })

    await user.keyboard('{ArrowDown}')
    expect(input).toHaveAttribute('aria-activedescendant', expect.stringContaining('option-0'))

    await user.keyboard('{ArrowDown}')
    expect(input).toHaveAttribute('aria-activedescendant', expect.stringContaining('option-1'))

    await user.keyboard('{Enter}')

    expect(onSearch).toHaveBeenCalledWith('running shoes')
    expect(input).toHaveValue('running shoes')
  }, 10000)

  it('should select a suggestion on click', async () => {
    mockAutocomplete.mockResolvedValueOnce(['red shoes'])
    const onSearch = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<ProductSearch onSearch={onSearch} />)

    await user.type(screen.getByRole('combobox'), 'sho')
    const option = await screen.findByRole('option', { name: 'red shoes' })
    await user.click(option)

    expect(onSearch).toHaveBeenCalledWith('red shoes')
    expect(screen.getByRole('combobox')).toHaveValue('red shoes')
  }, 10000)

  it('should close the suggestion list on Escape', async () => {
    mockAutocomplete.mockResolvedValueOnce(['red shoes'])
    const user = userEvent.setup()
    renderWithProviders(<ProductSearch onSearch={vi.fn()} />)

    await user.type(screen.getByRole('combobox'), 'sho')
    await screen.findByRole('option', { name: 'red shoes' })

    await user.keyboard('{Escape}')

    expect(screen.queryByRole('option')).not.toBeInTheDocument()
  }, 10000)

  it('should clear the input and run an empty search when the clear button is clicked', async () => {
    const onSearch = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<ProductSearch onSearch={onSearch} initialQuery="shoes" />)

    await user.click(screen.getByRole('button', { name: /clear search/i }))

    expect(screen.getByRole('combobox')).toHaveValue('')
    expect(onSearch).toHaveBeenCalledWith('')
  })

  it('should not show the clear button when the input is empty', () => {
    renderWithProviders(<ProductSearch onSearch={vi.fn()} />)
    expect(screen.queryByRole('button', { name: /clear search/i })).not.toBeInTheDocument()
  })

  it('should not fetch suggestions for the debounce period after each keystroke', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ProductSearch onSearch={vi.fn()} />)

    await user.type(screen.getByRole('combobox'), 'a')
    await wait(50)

    expect(mockAutocomplete).not.toHaveBeenCalled()
  })
})
