import { Link as RouterLink } from 'react-router-dom'
import { Box, Typography, Button } from '@mui/material'
import { designTokens } from '../../theme'

function NotFoundIllustration() {
  return (
    <Box
      component="svg"
      viewBox="0 0 320 200"
      aria-hidden="true"
      sx={{ width: '100%', maxWidth: 320, height: 'auto' }}
    >
      {/* Background circle */}
      <circle cx="160" cy="100" r="80" fill="rgba(99,102,241,0.08)" />
      {/* 4 left */}
      <text
        x="48"
        y="130"
        fontFamily="Inter, sans-serif"
        fontSize="90"
        fontWeight="700"
        fill="rgba(99,102,241,0.9)"
        letterSpacing="-4"
      >
        4
      </text>
      {/* 0 centre — astronaut face */}
      <circle cx="160" cy="100" r="38" fill="white" stroke="#6366F1" strokeWidth="4" />
      <circle cx="148" cy="95" r="5" fill="#6366F1" />
      <circle cx="172" cy="95" r="5" fill="#6366F1" />
      <path
        d="M 148 112 Q 160 122 172 112"
        stroke="#6366F1"
        strokeWidth="3"
        fill="none"
        strokeLinecap="round"
      />
      {/* Helmet visor shine */}
      <ellipse cx="170" cy="85" rx="6" ry="4" fill="rgba(99,102,241,0.2)" />
      {/* Antenna */}
      <line x1="160" y1="62" x2="160" y2="48" stroke="#6366F1" strokeWidth="3" strokeLinecap="round" />
      <circle cx="160" cy="44" r="5" fill="#8B5CF6" />
      {/* 4 right */}
      <text
        x="192"
        y="130"
        fontFamily="Inter, sans-serif"
        fontSize="90"
        fontWeight="700"
        fill="rgba(99,102,241,0.9)"
        letterSpacing="-4"
      >
        4
      </text>
      {/* Stars */}
      <circle cx="50" cy="40" r="2.5" fill="#F59E0B" />
      <circle cx="270" cy="55" r="2" fill="#10B981" />
      <circle cx="90" cy="165" r="1.5" fill="#6366F1" />
      <circle cx="240" cy="160" r="2" fill="#F59E0B" />
      <circle cx="295" cy="110" r="1.5" fill="#8B5CF6" />
      <circle cx="30" cy="120" r="2" fill="#3B82F6" />
    </Box>
  )
}

export function NotFound() {
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '60vh',
        textAlign: 'center',
        px: 3,
        py: { xs: 8, md: 12 },
      }}
      role="main"
    >
      <NotFoundIllustration />

      <Typography
        variant="h3"
        component="h1"
        sx={{
          mt: 4,
          mb: 1.5,
          fontWeight: 700,
          letterSpacing: '-0.02em',
          background: designTokens.gradients.cta,
          WebkitBackgroundClip: 'text',
          WebkitTextFillColor: 'transparent',
          backgroundClip: 'text',
        }}
      >
        Lost in space
      </Typography>

      <Typography
        variant="body1"
        color="text.secondary"
        sx={{ mb: 4, maxWidth: 380 }}
      >
        This page went on vacation without telling us. Our little astronaut is searching, but it might be a while.
      </Typography>

      <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', justifyContent: 'center' }}>
        <Button
          component={RouterLink}
          to="/"
          variant="contained"
          size="large"
          sx={{
            borderRadius: designTokens.radius.full,
            px: 4,
            background: designTokens.gradients.cta,
            boxShadow: designTokens.shadows.glow,
          }}
        >
          Go home
        </Button>

        <Button
          component={RouterLink}
          to="/products"
          variant="outlined"
          size="large"
          sx={{
            borderRadius: designTokens.radius.full,
            px: 4,
            borderWidth: 2,
            '&:hover': { borderWidth: 2 },
          }}
        >
          Browse products
        </Button>
      </Box>
    </Box>
  )
}
