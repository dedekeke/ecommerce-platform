import { Box, Typography, Divider, Grid } from '@mui/material'
import { useReviewSummary } from '../../hooks/useReviewSummary'
import { useProductReviews } from '../../hooks/useProductReviews'
import { useAuthUserId } from '../../hooks/useAuthUserId'
import { ReviewSummary } from './ReviewSummary'
import { ReviewList } from './ReviewList'
import { ReviewForm } from './ReviewForm'
import { SignInPrompt } from './SignInPrompt'

interface ProductReviewsSectionProps {
  productId: string
}

/**
 * PDP reviews widget: rating summary + paginated list + gated submission
 * form. Wires review-service (via the gateway's /v1/reviews route) into the
 * product detail page — the review-service backend was previously
 * end-to-end but had zero frontend consumers.
 */
export function ProductReviewsSection({ productId }: ProductReviewsSectionProps) {
  const summary = useReviewSummary(productId)
  const list = useProductReviews(productId)
  const userId = useAuthUserId()

  const handleSubmitted = () => {
    summary.refetch()
    list.setPage(0)
    list.refetch()
  }

  return (
    <Box data-testid="product-reviews-section" sx={{ mt: 6 }}>
      <Typography variant="h5" fontWeight={600} gutterBottom>
        Ratings &amp; Reviews
      </Typography>

      <Grid container spacing={4}>
        <Grid size={{ xs: 12, md: 4 }}>
          <ReviewSummary
            summary={summary.summary}
            isLoading={summary.isLoading}
            isError={summary.isError}
            onRetry={summary.refetch}
          />

          {userId ? <ReviewForm productId={productId} onSubmitted={handleSubmitted} /> : <SignInPrompt />}
        </Grid>

        <Grid size={{ xs: 12, md: 8 }}>
          <Divider sx={{ mb: 2, display: { xs: 'block', md: 'none' } }} />
          <ReviewList
            reviews={list.reviews}
            page={list.page}
            totalPages={list.totalPages}
            isLoading={list.isLoading}
            isError={list.isError}
            onPageChange={list.setPage}
            onRetry={list.refetch}
          />
        </Grid>
      </Grid>
    </Box>
  )
}

export default ProductReviewsSection
