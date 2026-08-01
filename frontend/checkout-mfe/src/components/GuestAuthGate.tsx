import { useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Divider from '@mui/material/Divider'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'

interface GuestAuthGateProps {
  /** Called with the normalized (trimmed) email once the shopper continues as guest. */
  onContinueAsGuest: (email: string) => void
}

// Lightweight client-side check; the gateway and order-service both re-validate
// the format server-side, so this only guards UX, never security.
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/**
 * Auth gate shown to an unauthenticated shopper. Instead of hard-blocking on
 * sign-in, it offers a guest path: enter an email and continue. The email is the
 * server-side guest identity seed and the claim key, so it is required + format
 * checked before proceeding.
 */
export default function GuestAuthGate({ onContinueAsGuest }: GuestAuthGateProps) {
  const [email, setEmail] = useState('')
  const [touched, setTouched] = useState(false)

  const trimmed = email.trim()
  const error = touched && !EMAIL_RE.test(trimmed) ? 'Enter a valid email address' : undefined

  const submit = () => {
    setTouched(true)
    if (EMAIL_RE.test(trimmed)) {
      onContinueAsGuest(trimmed)
    }
  }

  return (
    <Paper elevation={0} sx={{ p: { xs: 3, md: 4 }, borderRadius: 3, border: '1px solid', borderColor: 'divider' }}>
      <Typography variant="h6" fontWeight={700} sx={{ mb: 1 }}>
        Continue as guest
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        No account needed. Enter your email and we&apos;ll send your order confirmation there. You can
        create an account later to track this order.
      </Typography>

      <Box
        component="form"
        noValidate
        onSubmit={(e) => {
          e.preventDefault()
          submit()
        }}
      >
        <Stack spacing={2}>
          <TextField
            label="Email address"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            onBlur={() => setTouched(true)}
            error={Boolean(error)}
            helperText={error}
            fullWidth
            required
            autoComplete="email"
            inputProps={{ 'aria-label': 'Email address' }}
          />
          <Button type="submit" variant="contained" size="large" fullWidth>
            Continue as guest
          </Button>
        </Stack>
      </Box>

      <Divider sx={{ my: 3 }}>or</Divider>

      <Typography variant="body2" color="text.secondary" role="note">
        Have an account? Sign in from the top menu for faster checkout and full order history.
      </Typography>
    </Paper>
  )
}
