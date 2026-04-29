import { FormControl, InputLabel, Select, MenuItem, type SelectChangeEvent } from '@mui/material'

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
}

export function SortDropdown({ sortBy, sortDirection, onSortChange }: SortDropdownProps) {
  const currentValue = sortOptions.find(
    (opt) => opt.sortBy === sortBy && opt.sortDirection === sortDirection
  )?.value || 'newest'

  const handleChange = (event: SelectChangeEvent) => {
    const option = sortOptions.find((opt) => opt.value === event.target.value)
    if (option) {
      onSortChange(option.sortBy, option.sortDirection)
    }
  }

  return (
    <FormControl size="small" sx={{ minWidth: 180 }}>
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
}

export default SortDropdown
