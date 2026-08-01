import { FormControl, InputLabel, Select, MenuItem, Tooltip, type SelectChangeEvent } from '@mui/material'

type SortOption = {
  value: string
  label: string
  sortBy: 'name' | 'price' | 'createdAt'
  sortDirection: 'asc' | 'desc'
}

const sortOptions: SortOption[] = [
  { value: 'newest', label: 'Newest First', sortBy: 'createdAt', sortDirection: 'desc' },
  { value: 'oldest', label: 'Oldest First', sortBy: 'createdAt', sortDirection: 'asc' },
  { value: 'price-low', label: 'Price: Low to High', sortBy: 'price', sortDirection: 'asc' },
  { value: 'price-high', label: 'Price: High to Low', sortBy: 'price', sortDirection: 'desc' },
  { value: 'name-asc', label: 'Name: A to Z', sortBy: 'name', sortDirection: 'asc' },
  { value: 'name-desc', label: 'Name: Z to A', sortBy: 'name', sortDirection: 'desc' },
]

interface SortDropdownProps {
  sortBy: 'name' | 'price' | 'createdAt'
  sortDirection: 'asc' | 'desc'
  onSortChange: (sortBy: 'name' | 'price' | 'createdAt', sortDirection: 'asc' | 'desc') => void
  disabled?: boolean
}

export function SortDropdown({ sortBy, sortDirection, onSortChange, disabled = false }: SortDropdownProps) {
  const currentValue = sortOptions.find(
    (opt) => opt.sortBy === sortBy && opt.sortDirection === sortDirection
  )?.value || 'newest'

  const handleChange = (event: SelectChangeEvent) => {
    const option = sortOptions.find((opt) => opt.value === event.target.value)
    if (option) {
      onSortChange(option.sortBy, option.sortDirection)
    }
  }

  const control = (
    <FormControl size="small" sx={{ minWidth: 180 }} disabled={disabled}>
      <InputLabel id="sort-label">Sort by</InputLabel>
      <Select
        labelId="sort-label"
        value={currentValue}
        label="Sort by"
        onChange={handleChange}
        data-testid="sort-dropdown"
      >
        {sortOptions.map((option) => (
          <MenuItem key={option.value} value={option.value}>
            {option.label}
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  )

  if (!disabled) return control

  return (
    <Tooltip title="Sorting isn't available for search results">
      <span>{control}</span>
    </Tooltip>
  )
}

export default SortDropdown
