import { useEffect } from 'react'
import { useState } from 'react'
import { Box, TextField, Typography } from '@mui/material'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'

interface PriceRangeFilterProps {
  minPrice: number | null
  maxPrice: number | null
  onChange: (minPrice: number | null, maxPrice: number | null) => void
  debounceMs?: number
}

function toInputValue(value: number | null): string {
  return value !== null ? String(value) : ''
}

function parseInputValue(value: string): number | null {
  return value === '' ? null : Number(value)
}

export function PriceRangeFilter({ minPrice, maxPrice, onChange, debounceMs = 300 }: PriceRangeFilterProps) {
  const [minInput, setMinInput] = useState(toInputValue(minPrice))
  const [maxInput, setMaxInput] = useState(toInputValue(maxPrice))

  // Adjust local input state when the min/max props change externally (e.g. URL sync,
  // "Clear all"). Done during render per React's guidance, rather than in an effect, so it
  // doesn't cost an extra commit.
  const [prevMinPrice, setPrevMinPrice] = useState(minPrice)
  if (minPrice !== prevMinPrice) {
    setPrevMinPrice(minPrice)
    setMinInput(toInputValue(minPrice))
  }
  const [prevMaxPrice, setPrevMaxPrice] = useState(maxPrice)
  if (maxPrice !== prevMaxPrice) {
    setPrevMaxPrice(maxPrice)
    setMaxInput(toInputValue(maxPrice))
  }

  const debouncedMin = useDebouncedValue(minInput, debounceMs)
  const debouncedMax = useDebouncedValue(maxInput, debounceMs)

  const parsedMin = parseInputValue(debouncedMin)
  const parsedMax = parseInputValue(debouncedMax)
  const isInvalid =
    parsedMin !== null && parsedMax !== null && !Number.isNaN(parsedMin) && !Number.isNaN(parsedMax) && parsedMin > parsedMax

  useEffect(() => {
    if (isInvalid || Number.isNaN(parsedMin) || Number.isNaN(parsedMax)) return
    if (parsedMin === minPrice && parsedMax === maxPrice) return
    onChange(parsedMin, parsedMax)
  }, [debouncedMin, debouncedMax, isInvalid, parsedMin, parsedMax, minPrice, maxPrice, onChange])

  return (
    <Box>
      <Typography variant="subtitle2" sx={{ mb: 1 }}>
        Price Range
      </Typography>
      <Box sx={{ display: 'flex', gap: 1 }}>
        <TextField
          label="Min"
          type="number"
          size="small"
          fullWidth
          value={minInput}
          onChange={(event) => setMinInput(event.target.value)}
          inputProps={{ min: 0, 'aria-label': 'Minimum price' }}
        />
        <TextField
          label="Max"
          type="number"
          size="small"
          fullWidth
          value={maxInput}
          onChange={(event) => setMaxInput(event.target.value)}
          inputProps={{ min: 0, 'aria-label': 'Maximum price' }}
          error={isInvalid}
          helperText={isInvalid ? 'Max must be ≥ min' : ' '}
        />
      </Box>
    </Box>
  )
}

export default PriceRangeFilter
