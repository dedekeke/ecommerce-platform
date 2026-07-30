import { FormControl, InputLabel, Select, MenuItem, type SelectChangeEvent } from '@mui/material'
import type { Category } from '../../types'

interface CategorySelectProps {
  categories: Category[]
  value: string | null
  onChange: (categoryId: string | null) => void
  isLoading?: boolean
}

interface FlatOption {
  id: string
  label: string
}

function flatten(categories: Category[], depth = 0): FlatOption[] {
  return categories.flatMap((category) => {
    const id = category.slug || category.id
    const indent = '  '.repeat(depth)
    const children = category.children?.length ? flatten(category.children, depth + 1) : []
    return [{ id, label: `${indent}${category.name}` }, ...children]
  })
}

export function CategorySelect({ categories, value, onChange, isLoading = false }: CategorySelectProps) {
  const options = flatten(categories)

  const handleChange = (event: SelectChangeEvent) => {
    const next = event.target.value
    onChange(next === '' ? null : next)
  }

  return (
    <FormControl size="small" fullWidth disabled={isLoading}>
      <InputLabel id="category-filter-label">Category</InputLabel>
      <Select
        labelId="category-filter-label"
        label="Category"
        value={value ?? ''}
        onChange={handleChange}
        displayEmpty
        renderValue={(selected) => options.find((option) => option.id === selected)?.label ?? 'All Categories'}
        data-testid="category-select"
      >
        <MenuItem value="">All Categories</MenuItem>
        {options.map((option) => (
          <MenuItem key={option.id} value={option.id}>
            {option.label}
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  )
}

export default CategorySelect
