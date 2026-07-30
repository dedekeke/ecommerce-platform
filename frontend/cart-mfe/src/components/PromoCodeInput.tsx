import { useState, type KeyboardEvent } from 'react'
import Box from '@mui/material/Box'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import LocalOfferOutlinedIcon from '@mui/icons-material/LocalOfferOutlined'

interface PromoCodeInputProps {
  appliedCode: string | null
  isApplying: boolean
  error: string | null
  onApply: (code: string) => void
  onRemove: () => void
}

export default function PromoCodeInput({
  appliedCode,
  isApplying,
  error,
  onApply,
  onRemove,
}: PromoCodeInputProps) {
  const [code, setCode] = useState('')

  if (appliedCode) {
    return (
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
        <Chip
          icon={<LocalOfferOutlinedIcon />}
          label={`"${appliedCode}" applied`}
          color="success"
          variant="outlined"
          sx={{ fontWeight: 600, maxWidth: '75%' }}
        />
        <Button size="small" onClick={onRemove} aria-label="Remove promo code">
          Remove
        </Button>
      </Stack>
    )
  }

  const submit = () => {
    if (isApplying || code.trim().length === 0) return
    onApply(code)
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      event.preventDefault()
      submit()
    }
  }

  return (
    <Box sx={{ mb: 2 }}>
      <Stack direction="row" spacing={1} alignItems="flex-start">
        <TextField
          size="small"
          fullWidth
          label="Promo code"
          value={code}
          onChange={(event) => setCode(event.target.value.toUpperCase())}
          onKeyDown={handleKeyDown}
          error={Boolean(error)}
          helperText={error ?? ' '}
          disabled={isApplying}
          slotProps={{ formHelperText: { role: error ? 'alert' : undefined } }}
        />
        <Button
          variant="outlined"
          onClick={submit}
          disabled={isApplying || code.trim().length === 0}
          sx={{ minWidth: 96, height: 40 }}
        >
          {isApplying ? 'Applying…' : 'Apply'}
        </Button>
      </Stack>
    </Box>
  )
}
