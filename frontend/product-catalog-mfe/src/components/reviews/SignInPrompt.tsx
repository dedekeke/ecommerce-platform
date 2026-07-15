import { Alert } from '@mui/material'

/** Shown in place of the review form when `useAuthUserId()` returns null. */
export function SignInPrompt() {
  return (
    <Alert severity="info" role="alert" data-testid="review-signin-prompt">
      Sign in to write a review.
    </Alert>
  )
}

export default SignInPrompt
