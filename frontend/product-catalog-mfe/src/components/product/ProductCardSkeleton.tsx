import { Card, CardContent, CardActions, Skeleton, styled } from '@mui/material'

const StyledCard = styled(Card)(({ theme }) => ({
  height: '100%',
  display: 'flex',
  flexDirection: 'column',
  borderRadius: 16,
  border: `1px solid ${theme.palette.grey[100]}`,
}))

const ImageSkeleton = styled(Skeleton)({
  paddingTop: '100%',
  borderRadius: '16px 16px 0 0',
})

export function ProductCardSkeleton() {
  return (
    <StyledCard data-testid="product-card-skeleton">
      <ImageSkeleton variant="rectangular" animation="wave" />
      <CardContent sx={{ flexGrow: 1 }}>
        <Skeleton variant="text" width="40%" height={16} sx={{ mb: 0.5 }} />
        <Skeleton variant="text" width="90%" height={24} />
        <Skeleton variant="text" width="60%" height={24} sx={{ mb: 1 }} />
        <Skeleton variant="text" width="50%" height={32} />
      </CardContent>
      <CardActions sx={{ px: 2, pb: 2, pt: 0 }}>
        <Skeleton variant="rectangular" width="100%" height={40} sx={{ borderRadius: 2 }} />
      </CardActions>
    </StyledCard>
  )
}

export default ProductCardSkeleton
