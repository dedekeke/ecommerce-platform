import Box from '@mui/material/Box'
import Skeleton from '@mui/material/Skeleton'
import Stack from '@mui/material/Stack'
import Divider from '@mui/material/Divider'

export default function CartItemSkeleton() {
  return (
    <Box>
      <Stack direction="row" spacing={2} alignItems="center" sx={{ py: 2 }}>
        <Skeleton variant="rectangular" width={80} height={80} sx={{ borderRadius: 2, flexShrink: 0 }} />
        <Box sx={{ flex: 1 }}>
          <Skeleton variant="text" width="60%" height={24} sx={{ mb: 0.5 }} />
          <Skeleton variant="text" width="30%" height={20} />
        </Box>
        <Skeleton variant="rectangular" width={100} height={36} sx={{ borderRadius: 1 }} />
        <Skeleton variant="text" width={48} height={24} />
        <Skeleton variant="circular" width={32} height={32} />
      </Stack>
      <Divider />
    </Box>
  )
}
