import { useState, useEffect } from 'react'
import Box from '@mui/material/Box'
import Grid from '@mui/material/Grid'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import type { ShippingAddress } from '../api/types'

interface AddressFormProps {
  onValid: (address: ShippingAddress) => void
  onChange: () => void
  initialValues?: ShippingAddress
}

interface FormErrors {
  fullName?: string
  line1?: string
  city?: string
  state?: string
  postalCode?: string
  country?: string
}

interface TouchedFields {
  fullName?: boolean
  line1?: boolean
  city?: boolean
  state?: boolean
  postalCode?: boolean
  country?: boolean
}

const DEFAULT_VALUES: ShippingAddress = {
  fullName: '',
  line1: '',
  line2: '',
  city: '',
  state: '',
  postalCode: '',
  country: 'US',
}

function validate(values: ShippingAddress): FormErrors {
  const errors: FormErrors = {}
  if (!values.fullName.trim()) errors.fullName = 'Full name is required'
  if (!values.line1.trim()) errors.line1 = 'Address line 1 is required'
  if (!values.city.trim()) errors.city = 'City is required'
  if (!values.state.trim()) errors.state = 'State is required'
  if (!values.postalCode.trim()) errors.postalCode = 'Postal code is required'
  if (!values.country.trim()) errors.country = 'Country is required'
  return errors
}

export default function AddressForm({ onValid, onChange, initialValues }: AddressFormProps) {
  const [values, setValues] = useState<ShippingAddress>(initialValues ?? DEFAULT_VALUES)
  const [errors, setErrors] = useState<FormErrors>({})
  const [touched, setTouched] = useState<TouchedFields>({})

  useEffect(() => {
    const errs = validate(values)
    setErrors(errs)
    if (Object.keys(errs).length === 0) {
      onValid(values)
    }
  }, [values]) // eslint-disable-line react-hooks/exhaustive-deps

  const handleChange = (field: keyof ShippingAddress) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setValues((prev) => ({ ...prev, [field]: e.target.value }))
    onChange()
  }

  const handleBlur = (field: keyof TouchedFields) => () => {
    setTouched((prev) => ({ ...prev, [field]: true }))
  }

  const fieldError = (field: keyof FormErrors) =>
    touched[field] ? errors[field] : undefined

  return (
    <Box component="fieldset" sx={{ border: 'none', p: 0, m: 0 }}>
      <Typography
        component="legend"
        variant="h6"
        fontWeight={600}
        sx={{ mb: 3, display: 'block' }}
      >
        Shipping address
      </Typography>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12 }}>
          <TextField
            fullWidth
            label="Full name"
            id="fullName"
            inputProps={{ 'aria-describedby': fieldError('fullName') ? 'fullName-error' : undefined }}
            value={values.fullName}
            onChange={handleChange('fullName')}
            onBlur={handleBlur('fullName')}
            error={!!fieldError('fullName')}
            helperText={
              fieldError('fullName') ? (
                <span id="fullName-error" role="alert">
                  {fieldError('fullName')}
                </span>
              ) : undefined
            }
            required
            autoComplete="name"
          />
        </Grid>

        <Grid size={{ xs: 12 }}>
          <TextField
            fullWidth
            label="Address line 1"
            id="line1"
            inputProps={{ 'aria-describedby': fieldError('line1') ? 'line1-error' : undefined }}
            value={values.line1}
            onChange={handleChange('line1')}
            onBlur={handleBlur('line1')}
            error={!!fieldError('line1')}
            helperText={
              fieldError('line1') ? (
                <span id="line1-error" role="alert">
                  {fieldError('line1')}
                </span>
              ) : undefined
            }
            required
            autoComplete="address-line1"
          />
        </Grid>

        <Grid size={{ xs: 12 }}>
          <TextField
            fullWidth
            label="Address line 2"
            id="line2"
            value={values.line2 ?? ''}
            onChange={handleChange('line2')}
            autoComplete="address-line2"
            placeholder="Apartment, suite, etc. (optional)"
          />
        </Grid>

        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            fullWidth
            label="City"
            id="city"
            inputProps={{ 'aria-describedby': fieldError('city') ? 'city-error' : undefined }}
            value={values.city}
            onChange={handleChange('city')}
            onBlur={handleBlur('city')}
            error={!!fieldError('city')}
            helperText={
              fieldError('city') ? (
                <span id="city-error" role="alert">
                  {fieldError('city')}
                </span>
              ) : undefined
            }
            required
            autoComplete="address-level2"
          />
        </Grid>

        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            fullWidth
            label="State"
            id="state"
            inputProps={{ 'aria-describedby': fieldError('state') ? 'state-error' : undefined }}
            value={values.state}
            onChange={handleChange('state')}
            onBlur={handleBlur('state')}
            error={!!fieldError('state')}
            helperText={
              fieldError('state') ? (
                <span id="state-error" role="alert">
                  {fieldError('state')}
                </span>
              ) : undefined
            }
            required
            autoComplete="address-level1"
          />
        </Grid>

        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            fullWidth
            label="Postal code"
            id="postalCode"
            inputProps={{ 'aria-describedby': fieldError('postalCode') ? 'postalCode-error' : undefined }}
            value={values.postalCode}
            onChange={handleChange('postalCode')}
            onBlur={handleBlur('postalCode')}
            error={!!fieldError('postalCode')}
            helperText={
              fieldError('postalCode') ? (
                <span id="postalCode-error" role="alert">
                  {fieldError('postalCode')}
                </span>
              ) : undefined
            }
            required
            autoComplete="postal-code"
          />
        </Grid>

        <Grid size={{ xs: 12, sm: 6 }}>
          <TextField
            fullWidth
            label="Country"
            id="country"
            inputProps={{ 'aria-describedby': fieldError('country') ? 'country-error' : undefined }}
            value={values.country}
            onChange={handleChange('country')}
            onBlur={handleBlur('country')}
            error={!!fieldError('country')}
            helperText={
              fieldError('country') ? (
                <span id="country-error" role="alert">
                  {fieldError('country')}
                </span>
              ) : undefined
            }
            required
            autoComplete="country"
          />
        </Grid>
      </Grid>
    </Box>
  )
}
