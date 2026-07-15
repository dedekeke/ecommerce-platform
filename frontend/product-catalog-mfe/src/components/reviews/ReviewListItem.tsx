import { Box, Typography, Chip, Divider } from '@mui/material'
import { VerifiedUser as VerifiedIcon, ThumbUpOutlined as HelpfulIcon } from '@mui/icons-material'
import { StarRating } from './StarRating'
import type { Review } from '../../types'

interface ReviewListItemProps {
  review: Review
}

function formatDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
}

/** We never show the raw userId (Auth0 sub) — only a "Verified Purchase" signal. */
export function ReviewListItem({ review }: ReviewListItemProps) {
  return (
    <Box data-testid="review-list-item" component="article" sx={{ py: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5, flexWrap: 'wrap' }}>
        <StarRating value={review.rating} readOnly size="small" label={`${review.rating} out of 5 stars`} />
        {review.verified && (
          <Chip
            icon={<VerifiedIcon />}
            label="Verified Purchase"
            size="small"
            color="success"
            variant="outlined"
          />
        )}
        <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto' }}>
          {formatDate(review.createdAt)}
        </Typography>
      </Box>

      <Typography variant="subtitle1" fontWeight={600}>
        {review.title}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, whiteSpace: 'pre-wrap' }}>
        {review.body}
      </Typography>

      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 1, color: 'text.secondary' }}>
        <HelpfulIcon fontSize="small" />
        <Typography variant="caption">
          {review.helpful} found this helpful
        </Typography>
      </Box>

      <Divider sx={{ mt: 2 }} />
    </Box>
  )
}

export default ReviewListItem
