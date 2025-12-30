import { Box, Skeleton, Card, CardContent, Grid } from '@mui/material'

export const PageSkeleton = () => {
  return (
    <Box data-testid="page-skeleton" aria-label="Loading content">
      <Skeleton variant="rectangular" height={200} sx={{ mb: 3, borderRadius: 2 }} />
      <Skeleton variant="text" height={40} width="60%" sx={{ mb: 2 }} />
      <Skeleton variant="text" height={24} width="40%" sx={{ mb: 4 }} />
      <Grid container spacing={3}>
        {[1, 2, 3].map((i) => (
          <Grid size={{ xs: 12, sm: 6, md: 4 }} key={i}>
            <Skeleton variant="rectangular" height={150} sx={{ borderRadius: 2 }} />
          </Grid>
        ))}
      </Grid>
    </Box>
  )
}

export const ProductCardSkeleton = () => {
  return (
    <Card data-testid="product-card-skeleton" sx={{ height: '100%' }}>
      <Skeleton variant="rectangular" height={200} />
      <CardContent>
        <Skeleton variant="text" height={24} width="80%" />
        <Skeleton variant="text" height={20} width="60%" />
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 2 }}>
          <Skeleton variant="text" height={28} width="30%" />
          <Skeleton variant="circular" width={36} height={36} />
        </Box>
      </CardContent>
    </Card>
  )
}

interface ProductListSkeletonProps {
  count?: number
}

export const ProductListSkeleton = ({ count = 6 }: ProductListSkeletonProps) => {
  return (
    <Grid container spacing={3}>
      {Array.from({ length: count }).map((_, index) => (
        <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={index}>
          <ProductCardSkeleton />
        </Grid>
      ))}
    </Grid>
  )
}

export const CartItemSkeleton = () => {
  return (
    <Box sx={{ display: 'flex', gap: 2, p: 2, borderBottom: '1px solid', borderColor: 'divider' }}>
      <Skeleton variant="rectangular" width={80} height={80} sx={{ borderRadius: 1 }} />
      <Box sx={{ flex: 1 }}>
        <Skeleton variant="text" height={24} width="70%" />
        <Skeleton variant="text" height={20} width="40%" />
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 1 }}>
          <Skeleton variant="rectangular" width={100} height={32} sx={{ borderRadius: 1 }} />
          <Skeleton variant="text" height={24} width={60} />
        </Box>
      </Box>
    </Box>
  )
}

export const ProfileSkeleton = () => {
  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 3, mb: 4 }}>
        <Skeleton variant="circular" width={100} height={100} />
        <Box sx={{ flex: 1 }}>
          <Skeleton variant="text" height={32} width="50%" />
          <Skeleton variant="text" height={24} width="30%" />
        </Box>
      </Box>
      <Skeleton variant="rectangular" height={300} sx={{ borderRadius: 2 }} />
    </Box>
  )
}
