import { useState, type FormEvent } from 'react'
import { Box, Typography, TextField, Button, Alert } from '@mui/material'
import { StarRating } from './StarRating'
import { useSubmitReview } from '../../hooks/useSubmitReview'
import { toast } from '../../lib/toast'
import type { Review } from '../../types'

interface ReviewFormProps {
  productId: string
  onSubmitted?: (review: Review) => void
}

const TITLE_MAX = 200
const BODY_MAX = 4000

interface FieldErrors {
  rating?: string
  title?: string
  body?: string
}

function validate(rating: number, title: string, body: string): FieldErrors {
  const errors: FieldErrors = {}
  if (rating < 1 || rating > 5) errors.rating = 'Please select a rating from 1 to 5.'
  if (!title.trim()) errors.title = 'Title is required.'
  else if (title.length > TITLE_MAX) errors.title = `Title must be ${TITLE_MAX} characters or fewer.`
  if (!body.trim()) errors.body = 'Review text is required.'
  else if (body.length > BODY_MAX) errors.body = `Review must be ${BODY_MAX} characters or fewer.`
  return errors
}

/** Authenticated review submission form. Caller (ProductReviewsSection) gates rendering on auth. */
export function ReviewForm({ productId, onSubmitted }: ReviewFormProps) {
  const [rating, setRating] = useState(0)
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [showSuccess, setShowSuccess] = useState(false)

  const { submitReview, isSubmitting, error, resetError } = useSubmitReview()

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setShowSuccess(false)
    resetError()

    const errors = validate(rating, title, body)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    try {
      const review = await submitReview({ productId, rating, title: title.trim(), body: body.trim() })
      setRating(0)
      setTitle('')
      setBody('')
      setFieldErrors({})
      setShowSuccess(true)
      toast.success('Review submitted successfully')
      onSubmitted?.(review)
    } catch {
      // error is surfaced via the hook's `error` state
    }
  }

  return (
    <Box component="form" onSubmit={handleSubmit} noValidate sx={{ mt: 2 }}>
      <Typography variant="h6" fontWeight={600} gutterBottom>
        Write a review
      </Typography>

      {showSuccess && (
        <Alert severity="success" role="status" sx={{ mb: 2 }}>
          Thanks for your review!
        </Alert>
      )}
      {error && (
        <Alert severity="error" role="alert" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Box component="fieldset" sx={{ border: 'none', p: 0, m: 0, mb: 2 }}>
        <Typography component="legend" variant="body2" fontWeight={600} sx={{ mb: 0.5 }}>
          Your rating
        </Typography>
        <StarRating value={rating} onChange={setRating} readOnly={false} label="Your rating" />
        {fieldErrors.rating && (
          <Typography variant="caption" color="error" role="alert" sx={{ display: 'block', mt: 0.5 }}>
            {fieldErrors.rating}
          </Typography>
        )}
      </Box>

      <TextField
        label="Title"
        fullWidth
        required
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        error={!!fieldErrors.title}
        helperText={fieldErrors.title ?? `${title.length}/${TITLE_MAX}`}
        slotProps={{ htmlInput: { maxLength: TITLE_MAX } }}
        sx={{ mb: 2 }}
      />

      <TextField
        label="Your review"
        fullWidth
        required
        multiline
        minRows={4}
        value={body}
        onChange={(e) => setBody(e.target.value)}
        error={!!fieldErrors.body}
        helperText={fieldErrors.body ?? `${body.length}/${BODY_MAX}`}
        slotProps={{ htmlInput: { maxLength: BODY_MAX } }}
        sx={{ mb: 2 }}
      />

      <Button type="submit" variant="contained" disabled={isSubmitting}>
        {isSubmitting ? 'Submitting…' : 'Submit Review'}
      </Button>
    </Box>
  )
}

export default ReviewForm
