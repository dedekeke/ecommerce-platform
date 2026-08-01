import { Box, Button, Divider, Typography } from '@mui/material'
import { CategorySelect } from './CategorySelect'
import { PriceRangeFilter } from './PriceRangeFilter'
import { InStockToggle } from './InStockToggle'
import type { Category } from '../../types'

interface FilterPanelProps {
  categories: Category[]
  categoriesLoading?: boolean
  categoryId: string | null
  minPrice: number | null
  maxPrice: number | null
  inStockOnly: boolean
  onCategoryChange: (categoryId: string | null) => void
  onPriceChange: (minPrice: number | null, maxPrice: number | null) => void
  onInStockChange: (checked: boolean) => void
  onClear: () => void
}

export function FilterPanel({
  categories,
  categoriesLoading = false,
  categoryId,
  minPrice,
  maxPrice,
  inStockOnly,
  onCategoryChange,
  onPriceChange,
  onInStockChange,
  onClear,
}: FilterPanelProps) {
  const hasActiveFilters = Boolean(categoryId || minPrice !== null || maxPrice !== null || inStockOnly)

  return (
    <Box
      component="section"
      aria-label="Product filters"
      data-testid="filter-panel"
      sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2.5,
        p: 2.5,
        borderRadius: 3,
        border: '1px solid',
        borderColor: 'grey.100',
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="subtitle1" fontWeight={700}>
          Filters
        </Typography>
        {hasActiveFilters && (
          <Button size="small" onClick={onClear}>
            Clear all
          </Button>
        )}
      </Box>
      <Divider />
      <CategorySelect
        categories={categories}
        value={categoryId}
        onChange={onCategoryChange}
        isLoading={categoriesLoading}
      />
      <PriceRangeFilter minPrice={minPrice} maxPrice={maxPrice} onChange={onPriceChange} />
      <InStockToggle checked={inStockOnly} onChange={onInStockChange} />
    </Box>
  )
}

export default FilterPanel
