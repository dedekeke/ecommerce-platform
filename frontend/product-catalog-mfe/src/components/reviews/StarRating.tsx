import { Rating, Box, Typography } from '@mui/material'
import { Star as StarIcon, StarBorder as StarBorderIcon } from '@mui/icons-material'

interface StarRatingProps {
  value: number
  onChange?: (value: number) => void
  readOnly?: boolean
  size?: 'small' | 'medium' | 'large'
  /** Accessible label for the group; required when interactive. */
  label?: string
  showValue?: boolean
}

function ratingLabel(value: number): string {
  return `${value} Star${value !== 1 ? 's' : ''}`
}

/**
 * Thin accessible wrapper around MUI's `Rating`. In read-only mode it renders
 * product/review star ratings; in interactive mode (`readOnly={false}`) it's
 * the rating input for the review form — MUI already wires up the
 * radiogroup + keyboard nav, we just supply `getLabelText` and an
 * `aria-label` so screen readers announce "3 Stars" rather than a bare value.
 */
export function StarRating({
  value,
  onChange,
  readOnly = true,
  size = 'medium',
  label = 'Rating',
  showValue = false,
}: StarRatingProps) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <Rating
        value={value}
        onChange={(_, newValue) => onChange?.(newValue ?? 0)}
        readOnly={readOnly}
        size={size}
        precision={1}
        icon={<StarIcon fontSize="inherit" />}
        emptyIcon={<StarBorderIcon fontSize="inherit" />}
        getLabelText={ratingLabel}
        aria-label={label}
        sx={{ color: 'primary.main' }}
      />
      {showValue && (
        <Typography
          variant="body2"
          fontWeight={600}
          sx={{ fontFamily: '"JetBrains Mono", monospace' }}
        >
          {value.toFixed(1)}
        </Typography>
      )}
    </Box>
  )
}

export default StarRating
