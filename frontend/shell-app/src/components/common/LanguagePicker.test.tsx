import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { LanguagePicker } from './LanguagePicker'
import { useUserPreferencesStore } from '../../stores/userPreferencesStore'
import i18n from '../../i18n/i18n'

describe('LanguagePicker', () => {
  beforeEach(() => {
    useUserPreferencesStore.getState().resetPreferences()
    return i18n.changeLanguage('en')
  })

  it('renders the three supported language options', () => {
    render(<LanguagePicker />)
    fireEvent.mouseDown(screen.getByRole('combobox'))
    expect(screen.getByTestId('language-option-en')).toBeInTheDocument()
    expect(screen.getByTestId('language-option-vi')).toBeInTheDocument()
    expect(screen.getByTestId('language-option-es')).toBeInTheDocument()
  })

  it('updates the store and i18n language when a new locale is picked', async () => {
    render(<LanguagePicker />)
    fireEvent.mouseDown(screen.getByRole('combobox'))
    fireEvent.click(screen.getByTestId('language-option-vi'))

    expect(useUserPreferencesStore.getState().language).toBe('vi')
    expect(i18n.language).toBe('vi')
    expect(i18n.t('home.title')).toContain('E-Commerce')
  })
})
