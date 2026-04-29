// Real Stripe integration is a Day 40 follow-up. All payment method IDs here are stubs for dev/test.
import { useState, useEffect } from 'react'
import Box from '@mui/material/Box'
import FormControl from '@mui/material/FormControl'
import FormControlLabel from '@mui/material/FormControlLabel'
import FormHelperText from '@mui/material/FormHelperText'
import FormLabel from '@mui/material/FormLabel'
import Grid from '@mui/material/Grid'
import Radio from '@mui/material/Radio'
import RadioGroup from '@mui/material/RadioGroup'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import Alert from '@mui/material/Alert'
import CreditCardIcon from '@mui/icons-material/CreditCard'

type PaymentMethod = 'card' | 'paypal'

interface CardFields {
  cardNumber: string
  expiry: string
  cvv: string
}

interface CardErrors {
  cardNumber?: string
  expiry?: string
  cvv?: string
}

interface TouchedCard {
  cardNumber?: boolean
  expiry?: boolean
  cvv?: boolean
}

function validateCard(fields: CardFields): CardErrors {
  const errors: CardErrors = {}
  if (!fields.cardNumber.trim()) {
    errors.cardNumber = 'Card number is required'
  } else if (!/^\d{16}$/.test(fields.cardNumber.replace(/\s/g, ''))) {
    errors.cardNumber = 'Must be 16 digits'
  }
  if (!fields.expiry.trim()) {
    errors.expiry = 'Expiry is required'
  } else if (!/^(0[1-9]|1[0-2])\/\d{2}$/.test(fields.expiry)) {
    errors.expiry = 'Invalid expiry (MM/YY)'
  }
  if (!fields.cvv.trim()) {
    errors.cvv = 'CVV is required'
  } else if (!/^\d{3,4}$/.test(fields.cvv)) {
    errors.cvv = 'CVV must be 3-4 digits'
  }
  return errors
}

interface PaymentMethodFormProps {
  onPaymentMethodReady: (paymentMethodId: string) => void
}

export default function PaymentMethodForm({ onPaymentMethodReady }: PaymentMethodFormProps) {
  const [method, setMethod] = useState<PaymentMethod>('card')
  const [cardFields, setCardFields] = useState<CardFields>({ cardNumber: '', expiry: '', cvv: '' })
  const [cardErrors, setCardErrors] = useState<CardErrors>({})
  const [touched, setTouched] = useState<TouchedCard>({})

  useEffect(() => {
    if (method === 'paypal') {
      onPaymentMethodReady(`mock_paypal_${Date.now()}`)
      return
    }
    const errors = validateCard(cardFields)
    setCardErrors(errors)
    if (Object.keys(errors).length === 0) {
      onPaymentMethodReady(`mock_card_${Date.now()}`)
    }
  }, [method, cardFields]) // eslint-disable-line react-hooks/exhaustive-deps

  const handleCardField =
    (field: keyof CardFields) => (e: React.ChangeEvent<HTMLInputElement>) => {
      setCardFields((prev) => ({ ...prev, [field]: e.target.value }))
    }

  const handleBlur = (field: keyof TouchedCard) => () => {
    setTouched((prev) => ({ ...prev, [field]: true }))
  }

  const fieldError = (field: keyof CardErrors) => (touched[field] ? cardErrors[field] : undefined)

  return (
    <Box component="fieldset" sx={{ border: 'none', p: 0, m: 0 }}>
      <Typography component="legend" variant="h6" fontWeight={600} sx={{ mb: 3, display: 'block' }}>
        Payment method
      </Typography>

      <Alert severity="info" sx={{ mb: 3 }} icon={<CreditCardIcon />}>
        Test mode — no real charges. Stripe integration coming Day 40.
      </Alert>

      <FormControl component="fieldset" sx={{ mb: 3 }}>
        <FormLabel component="legend" sx={{ mb: 1, fontWeight: 500 }}>
          Select method
        </FormLabel>
        <RadioGroup
          row
          value={method}
          onChange={(e) => setMethod(e.target.value as PaymentMethod)}
          aria-label="payment method"
        >
          <FormControlLabel value="card" control={<Radio />} label="Card" />
          <FormControlLabel value="paypal" control={<Radio />} label="PayPal" />
        </RadioGroup>
      </FormControl>

      {method === 'card' && (
        <Grid container spacing={2}>
          <Grid size={{ xs: 12 }}>
            <TextField
              fullWidth
              label="Card number"
              id="cardNumber"
              value={cardFields.cardNumber}
              onChange={handleCardField('cardNumber')}
              onBlur={handleBlur('cardNumber')}
              error={!!fieldError('cardNumber')}
              helperText={
                fieldError('cardNumber') ? (
                  <span id="cardNumber-error" role="alert">
                    {fieldError('cardNumber')}
                  </span>
                ) : (
                  <FormHelperText component="span">16-digit card number</FormHelperText>
                )
              }
              inputProps={{
                'aria-describedby': fieldError('cardNumber') ? 'cardNumber-error' : undefined,
                maxLength: 16,
                inputMode: 'numeric',
              }}
              placeholder="1234 5678 9012 3456"
            />
          </Grid>

          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              fullWidth
              label="Expiry"
              id="expiry"
              value={cardFields.expiry}
              onChange={handleCardField('expiry')}
              onBlur={handleBlur('expiry')}
              error={!!fieldError('expiry')}
              helperText={
                fieldError('expiry') ? (
                  <span id="expiry-error" role="alert">
                    {fieldError('expiry')}
                  </span>
                ) : (
                  <FormHelperText component="span">MM/YY</FormHelperText>
                )
              }
              inputProps={{
                'aria-describedby': fieldError('expiry') ? 'expiry-error' : undefined,
                maxLength: 5,
              }}
              placeholder="MM/YY"
            />
          </Grid>

          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              fullWidth
              label="CVV"
              id="cvv"
              value={cardFields.cvv}
              onChange={handleCardField('cvv')}
              onBlur={handleBlur('cvv')}
              error={!!fieldError('cvv')}
              helperText={
                fieldError('cvv') ? (
                  <span id="cvv-error" role="alert">
                    {fieldError('cvv')}
                  </span>
                ) : (
                  <FormHelperText component="span">3 or 4 digits</FormHelperText>
                )
              }
              inputProps={{
                'aria-describedby': fieldError('cvv') ? 'cvv-error' : undefined,
                maxLength: 4,
                inputMode: 'numeric',
              }}
              type="password"
              placeholder="•••"
            />
          </Grid>
        </Grid>
      )}

      {method === 'paypal' && (
        <Box
          sx={{
            p: 3,
            border: '2px dashed',
            borderColor: 'secondary.light',
            borderRadius: 2,
            textAlign: 'center',
          }}
        >
          <Typography variant="body2" color="text.secondary">
            You will be redirected to PayPal to complete your payment.
          </Typography>
        </Box>
      )}
    </Box>
  )
}
