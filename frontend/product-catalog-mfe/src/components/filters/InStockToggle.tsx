import { FormControlLabel, Switch } from '@mui/material'

interface InStockToggleProps {
  checked: boolean
  onChange: (checked: boolean) => void
}

export function InStockToggle({ checked, onChange }: InStockToggleProps) {
  return (
    <FormControlLabel
      control={
        <Switch checked={checked} onChange={(event) => onChange(event.target.checked)} />
      }
      label="In stock only"
    />
  )
}

export default InStockToggle
