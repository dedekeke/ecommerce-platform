import { Select, MenuItem, FormControl, InputLabel } from '@mui/material'
import type { SelectChangeEvent } from '@mui/material/Select'
import { useUserPreferencesStore, selectCurrency } from '../../stores/userPreferencesStore'
import type { Currency } from '../../stores/types'

const SUPPORTED_CURRENCIES: Currency[] = ['USD', 'EUR', 'GBP', 'JPY', 'VND', 'CAD']

/**
 * Header dropdown that lets the user pick a display currency. The value
 * persists via {@link useUserPreferencesStore} (localStorage-backed). MFEs
 * subscribe via {@code selectCurrency} to re-render prices on change (§3.5).
 */
export const CurrencyPicker = () => {
  const currency = useUserPreferencesStore(selectCurrency)
  const setCurrency = useUserPreferencesStore((state) => state.setCurrency)

  const handleChange = (event: SelectChangeEvent<Currency>) => {
    setCurrency(event.target.value as Currency)
  }

  return (
    <FormControl size="small" sx={{ minWidth: 96, ml: 1 }}>
      <InputLabel id="currency-picker-label" sx={{ fontSize: '0.8rem' }}>
        Currency
      </InputLabel>
      <Select<Currency>
        labelId="currency-picker-label"
        value={currency}
        label="Currency"
        onChange={handleChange}
        inputProps={{ 'aria-label': 'currency selector', 'data-testid': 'currency-picker' }}
        sx={{ fontSize: '0.85rem' }}
      >
        {SUPPORTED_CURRENCIES.map((code) => (
          <MenuItem key={code} value={code} data-testid={`currency-option-${code}`}>
            {code}
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  )
}

export default CurrencyPicker
