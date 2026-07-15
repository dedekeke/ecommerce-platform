import { Box, Typography, Skeleton, Button, Pagination as MuiPagination } from '@mui/material'
import { ReviewListItem } from './ReviewListItem'
import type { Review } from '../../types'

interface ReviewListProps {
  reviews: Review[]
  page: number
  totalPages: number
  isLoading?: boolean
  isError?: boolean
  onPageChange: (page: number) => void
  onRetry?: () => void
}

export function ReviewList({
  reviews,
  page,
  totalPages,
  isLoading = false,
  isError = false,
  onPageChange,
  onRetry,
}: ReviewListProps) {
  if (isLoading) {
    return (
      <Box data-testid="review-list-skeleton">
        {Array.from({ length: 3 }).map((_, i) => (
          <Box key={i} sx={{ mb: 2 }}>
            <Skeleton variant="text" width={120} height={24} />
            <Skeleton variant="text" width="90%" />
            <Skeleton variant="text" width="70%" />
          </Box>
        ))}
      </Box>
    )
  }

  if (isError) {
    return (
      <Box>
        <Typography color="error" role="alert" sx={{ mb: 1 }}>
          Unable to load reviews right now.
        </Typography>
        {onRetry && (
          <Button size="small" variant="outlined" onClick={onRetry}>
            Retry
          </Button>
        )}
      </Box>
    )
  }

  if (reviews.length === 0) {
    return (
      <Box data-testid="review-list-empty">
        <Typography color="text.secondary">No reviews yet.</Typography>
      </Box>
    )
  }

  return (
    <Box data-testid="review-list">
      {reviews.map((review) => (
        <ReviewListItem key={review.id} review={review} />
      ))}

      {totalPages > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
          <MuiPagination
            count={totalPages}
            page={page + 1}
            onChange={(_, value) => onPageChange(value - 1)}
            color="primary"
            shape="rounded"
          />
        </Box>
      )}
    </Box>
  )
}

export default ReviewList
