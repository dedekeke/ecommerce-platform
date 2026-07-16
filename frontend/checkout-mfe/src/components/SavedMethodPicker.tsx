import { useCallback, useEffect, useState } from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import FormControlLabel from '@mui/material/FormControlLabel'
import Paper from '@mui/material/Paper'
import Radio from '@mui/material/Radio'
import RadioGroup from '@mui/material/RadioGroup'
import Skeleton from '@mui/material/Skeleton'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { listSavedMethods } from '../api/paymentMethodsService'
import type { SavedPaymentMethod } from '../api/types'

export type SavedMethodSelection = { type: 'new' } | { type: 'saved'; method: SavedPaymentMethod }

export interface SavedMethodPickerProps {
  /** Authenticated user id whose saved methods to list. Never called for guests. */
  userId: string
  /** Called whenever the effective selection changes, including the initial resolution once the
   * saved methods have loaded (or failed to load / turned out empty). */
  onSelectionChange: (selection: SavedMethodSelection) => void
}

const NEW_CARD_VALUE = 'new'

const formatExpiry = (month: number, year: number) => `${String(month).padStart(2, '0')}/${year}`

/**
 * Saved-card selector shown at the Payment step for authenticated shoppers only. Fetches the
 * user's saved Stripe payment methods (`GET /payments/methods/user/{userId}`) so they can reuse
 * one instead of retyping a card. Falls through silently to the new-card flow when there are no
 * saved methods or the fetch fails — this is a convenience feature and must never block checkout.
 */
export default function SavedMethodPicker({ userId, onSelectionChange }: SavedMethodPickerProps) {
  const [methods, setMethods] = useState<SavedPaymentMethod[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [selectedValue, setSelectedValue] = useState<string>(NEW_CARD_VALUE)

  useEffect(() => {
    let cancelled = false

    listSavedMethods(userId)
      .then((result) => {
        if (cancelled) return
        setMethods(result)
        const defaultMethod = result.find((m) => m.isDefault) ?? result[0]
        if (defaultMethod) {
          setSelectedValue(String(defaultMethod.id))
          onSelectionChange({ type: 'saved', method: defaultMethod })
        } else {
          onSelectionChange({ type: 'new' })
        }
      })
      .catch(() => {
        if (cancelled) return
        setMethods([])
        setError('Could not load your saved payment methods. You can still pay with a new card.')
        onSelectionChange({ type: 'new' })
      })

    return () => {
      cancelled = true
    }
  }, [userId, onSelectionChange])

  const handleChange = useCallback(
    (value: string) => {
      setSelectedValue(value)
      if (value === NEW_CARD_VALUE) {
        onSelectionChange({ type: 'new' })
        return
      }
      const method = methods?.find((m) => String(m.id) === value)
      if (method) onSelectionChange({ type: 'saved', method })
    },
    [methods, onSelectionChange]
  )

  if (methods === null) {
    return (
      <Box sx={{ mb: 3 }}>
        <Skeleton
          variant="rounded"
          height={56}
          sx={{ mb: 1 }}
          aria-label="Loading saved payment methods"
        />
        <Skeleton variant="rounded" height={56} />
      </Box>
    )
  }

  if (error) {
    return (
      <Alert severity="error" sx={{ mb: 3 }} role="alert">
        {error}
      </Alert>
    )
  }

  if (methods.length === 0) {
    return null
  }

  return (
    <Paper
      elevation={0}
      sx={{ p: { xs: 2.5, md: 3 }, mb: 3, borderRadius: 3, border: '1px solid', borderColor: 'divider' }}
    >
      <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 2 }}>
        Payment method
      </Typography>
      <RadioGroup
        aria-label="Choose a payment method"
        value={selectedValue}
        onChange={(e) => handleChange(e.target.value)}
      >
        <Stack spacing={1.5}>
          {methods.map((method) => (
            <FormControlLabel
              key={method.id}
              value={String(method.id)}
              control={<Radio />}
              label={
                <Stack direction="row" spacing={1} alignItems="center">
                  <Typography variant="body2">
                    {method.brand.toUpperCase()} &bull;&bull;&bull;&bull; {method.last4} &middot; exp{' '}
                    {formatExpiry(method.expMonth, method.expYear)}
                  </Typography>
                  {method.isDefault && (
                    <Chip label="Default" size="small" color="primary" variant="outlined" />
                  )}
                </Stack>
              }
            />
          ))}
          <FormControlLabel value={NEW_CARD_VALUE} control={<Radio />} label="Use a new card" />
        </Stack>
      </RadioGroup>
    </Paper>
  )
}
