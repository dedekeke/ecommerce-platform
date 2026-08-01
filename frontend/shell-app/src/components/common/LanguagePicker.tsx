import { Select, MenuItem, FormControl, InputLabel } from '@mui/material'
import type { SelectChangeEvent } from '@mui/material/Select'
import { useTranslation } from 'react-i18next'
import { useUserPreferencesStore, selectLanguage } from '../../stores/userPreferencesStore'
import type { Language } from '../../stores/types'

const SUPPORTED_LANGUAGES: { code: Language; label: string }[] = [
  { code: 'en', label: 'EN' },
  { code: 'vi', label: 'VI' },
  { code: 'es', label: 'ES' },
]

/**
 * §3.5 Header dropdown that lets the user pick the UI language.
 *
 * Updates two things in lockstep:
 *  1. {@code userPreferencesStore.language} — persisted via zustand-persist
 *     so MFEs subscribed to the federated singleton can react.
 *  2. {@code i18n.changeLanguage()} — switches the live translation set.
 */
export const LanguagePicker = () => {
  const { i18n, t } = useTranslation()
  const language = useUserPreferencesStore(selectLanguage)
  const setLanguage = useUserPreferencesStore((state) => state.setLanguage)

  const handleChange = (event: SelectChangeEvent<Language>) => {
    const next = event.target.value as Language
    setLanguage(next)
    void i18n.changeLanguage(next)
  }

  // The store may hold a Language value (e.g. 'fr') we don't expose in the
  // picker yet. Fall back to 'en' to avoid an out-of-range Select warning.
  const selected: Language = SUPPORTED_LANGUAGES.some((l) => l.code === language)
    ? language
    : 'en'

  return (
    <FormControl size="small" sx={{ minWidth: 80, ml: 1 }}>
      <InputLabel id="language-picker-label" sx={{ fontSize: '0.8rem' }}>
        {t('header.language')}
      </InputLabel>
      <Select<Language>
        labelId="language-picker-label"
        value={selected}
        label={t('header.language')}
        onChange={handleChange}
        inputProps={{ 'aria-label': 'language selector', 'data-testid': 'language-picker' }}
        sx={{ fontSize: '0.85rem' }}
      >
        {SUPPORTED_LANGUAGES.map(({ code, label }) => (
          <MenuItem key={code} value={code} data-testid={`language-option-${code}`}>
            {label}
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  )
}

export default LanguagePicker
