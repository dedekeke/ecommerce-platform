import { useId, useRef, useState, type KeyboardEvent } from 'react'
import { Box, InputBase, IconButton, Paper, List, ListItemButton, ListItemText, CircularProgress } from '@mui/material'
import { Search as SearchIcon, Close as ClearIcon } from '@mui/icons-material'
import { useAutocomplete } from '../../hooks/useAutocomplete'

interface ProductSearchProps {
  initialQuery?: string
  onSearch: (query: string) => void
  placeholder?: string
}

export function ProductSearch({
  initialQuery = '',
  onSearch,
  placeholder = 'Search products...',
}: ProductSearchProps) {
  const [inputValue, setInputValue] = useState(initialQuery)
  const [isOpen, setIsOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(-1)
  const listboxId = useId()
  const inputRef = useRef<HTMLInputElement>(null)

  const { suggestions, isLoading } = useAutocomplete(inputValue)
  const showSuggestions = isOpen && suggestions.length > 0

  const runSearch = (query: string) => {
    setIsOpen(false)
    setActiveIndex(-1)
    onSearch(query.trim())
  }

  const selectSuggestion = (suggestion: string) => {
    setInputValue(suggestion)
    runSearch(suggestion)
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      if (!suggestions.length) return
      event.preventDefault()
      setIsOpen(true)
      setActiveIndex((prev) => (prev + 1) % suggestions.length)
    } else if (event.key === 'ArrowUp') {
      if (!suggestions.length) return
      event.preventDefault()
      setIsOpen(true)
      setActiveIndex((prev) => (prev <= 0 ? suggestions.length - 1 : prev - 1))
    } else if (event.key === 'Enter') {
      event.preventDefault()
      if (isOpen && activeIndex >= 0 && suggestions[activeIndex]) {
        selectSuggestion(suggestions[activeIndex])
      } else {
        runSearch(inputValue)
      }
    } else if (event.key === 'Escape') {
      setIsOpen(false)
      setActiveIndex(-1)
    }
  }

  return (
    <Box sx={{ position: 'relative', width: '100%', maxWidth: 480 }}>
      <Paper
        component="form"
        role="search"
        elevation={0}
        onSubmit={(event) => {
          event.preventDefault()
          runSearch(inputValue)
        }}
        sx={{
          display: 'flex',
          alignItems: 'center',
          px: 2,
          py: 0.5,
          border: '2px solid',
          borderColor: 'grey.200',
          borderRadius: 3,
          transition: 'border-color 0.15s ease-out',
          '&:focus-within': { borderColor: 'primary.main' },
        }}
      >
        <SearchIcon sx={{ color: 'text.secondary', mr: 1 }} fontSize="small" />
        <InputBase
          inputRef={inputRef}
          fullWidth
          value={inputValue}
          onChange={(event) => {
            setInputValue(event.target.value)
            setIsOpen(true)
            setActiveIndex(-1)
          }}
          onFocus={() => setIsOpen(true)}
          onBlur={() => setIsOpen(false)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          inputProps={{
            'aria-label': 'Search products',
            role: 'combobox',
            'aria-expanded': showSuggestions,
            'aria-controls': listboxId,
            'aria-autocomplete': 'list',
            'aria-activedescendant': activeIndex >= 0 ? `${listboxId}-option-${activeIndex}` : undefined,
          }}
        />
        {isLoading && <CircularProgress size={16} sx={{ mx: 1 }} aria-label="Loading suggestions" />}
        {inputValue && (
          <IconButton
            size="small"
            aria-label="Clear search"
            onMouseDown={(event) => event.preventDefault()}
            onClick={() => {
              setInputValue('')
              runSearch('')
              inputRef.current?.focus()
            }}
          >
            <ClearIcon fontSize="small" />
          </IconButton>
        )}
      </Paper>

      {showSuggestions && (
        <Paper
          id={listboxId}
          role="listbox"
          aria-label="Search suggestions"
          sx={{
            position: 'absolute',
            top: '100%',
            left: 0,
            right: 0,
            mt: 0.5,
            zIndex: (theme) => theme.zIndex.appBar + 1,
            maxHeight: 320,
            overflowY: 'auto',
          }}
        >
          <List disablePadding>
            {suggestions.map((suggestion, index) => (
              <ListItemButton
                key={suggestion}
                id={`${listboxId}-option-${index}`}
                role="option"
                aria-selected={index === activeIndex}
                selected={index === activeIndex}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => selectSuggestion(suggestion)}
              >
                <ListItemText primary={suggestion} />
              </ListItemButton>
            ))}
          </List>
        </Paper>
      )}
    </Box>
  )
}

export default ProductSearch
