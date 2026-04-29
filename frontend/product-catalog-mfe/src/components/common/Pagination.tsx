import { Box, Pagination as MuiPagination, Typography, Select, MenuItem, FormControl, InputLabel, type SelectChangeEvent } from '@mui/material'

interface PaginationProps {
  page: number
  totalPages: number
  totalElements: number
  pageSize: number
  onPageChange: (page: number) => void
  onPageSizeChange?: (size: number) => void
  pageSizeOptions?: number[]
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  pageSize,
  onPageChange,
  onPageSizeChange,
  pageSizeOptions = [12, 24, 48],
}: PaginationProps) {
  const handlePageChange = (_: React.ChangeEvent<unknown>, value: number) => {
    onPageChange(value - 1)
  }

  const handlePageSizeChange = (event: SelectChangeEvent<number>) => {
    onPageSizeChange?.(event.target.value as number)
  }

  const startItem = page * pageSize + 1
  const endItem = Math.min((page + 1) * pageSize, totalElements)

  return (
    <Box
      data-testid="pagination"
      sx={{
        display: 'flex',
        flexDirection: { xs: 'column', sm: 'row' },
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 2,
        mt: 4,
        py: 2,
      }}
    >
      <Typography variant="body2" color="text.secondary">
        Showing {startItem}-{endItem} of {totalElements} products
      </Typography>

      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
        {onPageSizeChange && (
          <FormControl size="small" sx={{ minWidth: 100 }}>
            <InputLabel id="page-size-label">Per page</InputLabel>
            <Select
              labelId="page-size-label"
              value={pageSize}
              label="Per page"
              onChange={handlePageSizeChange}
            >
              {pageSizeOptions.map((size) => (
                <MenuItem key={size} value={size}>
                  {size}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        )}

        <MuiPagination
          count={totalPages}
          page={page + 1}
          onChange={handlePageChange}
          color="primary"
          shape="rounded"
          showFirstButton
          showLastButton
        />
      </Box>
    </Box>
  )
}

export default Pagination
