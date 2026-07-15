import { Box, Typography, LinearProgress, Skeleton, Button } from '@mui/material'
import { StarRating } from './StarRating'
import type { ReviewSummary as ReviewSummaryData } from '../../types'

interface ReviewSummaryProps {
  summary: ReviewSummaryData | null
  isLoading?: boolean
  isError?: boolean
  onRetry?: () => void
}

const RATINGS = [5, 4, 3, 2, 1]

export function ReviewSummary({ summary, isLoading = false, isError = false, onRetry }: ReviewSummaryProps) {
  if (isLoading) {
    return (
      <Box data-testid="review-summary-skeleton" sx={{ mb: 3 }}>
        <Skeleton variant="text" width={120} height={48} />
        <Skeleton variant="text" width={180} height={24} sx={{ mb: 2 }} />
        {RATINGS.map((r) => (
          <Skeleton key={r} variant="rectangular" height={16} sx={{ mb: 1, borderRadius: 1 }} />
        ))}
      </Box>
    )
  }

  if (isError) {
    return (
      <Box sx={{ mb: 3 }}>
        <Typography color="error" role="alert" sx={{ mb: 1 }}>
          Unable to load the rating summary.
        </Typography>
        {onRetry && (
          <Button size="small" variant="outlined" onClick={onRetry}>
            Retry
          </Button>
        )}
      </Box>
    )
  }

  if (!summary || summary.count === 0) {
    return (
      <Box data-testid="review-summary-empty" sx={{ mb: 3 }}>
        <Typography color="text.secondary">
          No reviews yet — be the first to review this product.
        </Typography>
      </Box>
    )
  }

  return (
    <Box data-testid="review-summary" sx={{ mb: 3 }}>
      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 2, mb: 1 }}>
        <Typography variant="h3" fontWeight={700} sx={{ fontFamily: '"JetBrains Mono", monospace' }}>
          {summary.averageRating.toFixed(1)}
        </Typography>
        <Box>
          <StarRating value={summary.averageRating} readOnly label="Average rating" />
          <Typography variant="body2" color="text.secondary">
            Based on {summary.count} {summary.count === 1 ? 'review' : 'reviews'}
          </Typography>
        </Box>
      </Box>

      <Box sx={{ mt: 2 }}>
        {RATINGS.map((rating) => {
          const ratingCount = summary.distribution[rating] ?? 0
          const pct = summary.count > 0 ? (ratingCount / summary.count) * 100 : 0
          return (
            <Box
              key={rating}
              sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}
            >
              <Typography variant="caption" sx={{ minWidth: 48 }}>
                {rating} star
              </Typography>
              <LinearProgress
                variant="determinate"
                value={pct}
                aria-label={`${rating} star: ${ratingCount} reviews`}
                sx={{ flexGrow: 1, height: 8, borderRadius: 4 }}
              />
              <Typography variant="caption" color="text.secondary" sx={{ minWidth: 24, textAlign: 'right' }}>
                {ratingCount}
              </Typography>
            </Box>
          )
        })}
      </Box>
    </Box>
  )
}

export default ReviewSummary
