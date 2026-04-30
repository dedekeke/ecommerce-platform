import { createTheme, responsiveFontSizes } from '@mui/material/styles'

declare module '@mui/material/styles' {
  interface Palette {
    neutral: Palette['primary']
  }
  interface PaletteOptions {
    neutral?: PaletteOptions['primary']
  }
}

// Design tokens from docs/frontend-design-brief.md
export const designTokens = {
  colors: {
    primary: '#0A0A0A',
    secondary: '#FAFAFA',
    accent: '#6366F1',
    accentHover: '#4F46E5',
    success: '#10B981',
    warning: '#F59E0B',
    error: '#EF4444',
    info: '#3B82F6',
    gray50: '#FAFAFA',
    gray100: '#F4F4F5',
    gray200: '#E4E4E7',
    gray300: '#D4D4D8',
    gray400: '#A1A1AA',
    gray500: '#71717A',
    gray600: '#52525B',
    gray700: '#3F3F46',
    gray800: '#27272A',
    gray900: '#18181B',
  },
  gradients: {
    hero: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    cta: 'linear-gradient(90deg, #6366F1 0%, #8B5CF6 100%)',
    success: 'linear-gradient(90deg, #10B981 0%, #34D399 100%)',
    premium: 'linear-gradient(135deg, #F59E0B 0%, #EF4444 100%)',
  },
  glass: {
    bg: 'rgba(255, 255, 255, 0.72)',
    bgDark: 'rgba(20, 20, 30, 0.72)',
    blur: 'blur(10px)',
    border: '1px solid rgba(255, 255, 255, 0.2)',
  },
  shadows: {
    sm: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
    md: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -2px rgba(0, 0, 0, 0.1)',
    lg: '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -4px rgba(0, 0, 0, 0.1)',
    xl: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
    glow: '0 0 20px rgba(99, 102, 241, 0.3)',
    cardHover: '0 20px 40px -10px rgba(0, 0, 0, 0.15)',
    elevation: '0 4px 16px rgba(0, 0, 0, 0.12)',
  },
  radius: {
    sm: '6px',
    md: '12px',
    lg: '16px',
    xl: '24px',
    '2xl': '32px',
    full: '9999px',
  },
  easing: {
    out: 'cubic-bezier(0.16, 1, 0.3, 1)',
    bounce: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
    spring: 'cubic-bezier(0.22, 1, 0.36, 1)',
  },
  duration: {
    fast: '0.15s',
    normal: '0.2s',
    slow: '0.3s',
    page: '0.5s',
  },
} as const

const buildTheme = (mode: 'light' | 'dark') => {
  const isDark = mode === 'dark'

  return createTheme({
    palette: {
      mode,
      primary: {
        main: designTokens.colors.accent,
        light: '#818CF8',
        dark: designTokens.colors.accentHover,
        contrastText: '#ffffff',
      },
      secondary: {
        main: designTokens.colors.info,
        light: '#60A5FA',
        dark: '#2563EB',
        contrastText: '#ffffff',
      },
      error: {
        main: designTokens.colors.error,
        light: '#FCA5A5',
        dark: '#DC2626',
      },
      warning: {
        main: designTokens.colors.warning,
        light: '#FCD34D',
        dark: '#D97706',
      },
      success: {
        main: designTokens.colors.success,
        light: '#6EE7B7',
        dark: '#059669',
      },
      neutral: {
        main: designTokens.colors.gray500,
        light: designTokens.colors.gray400,
        dark: designTokens.colors.gray600,
        contrastText: '#ffffff',
      },
      background: {
        default: isDark ? designTokens.colors.gray900 : designTokens.colors.gray50,
        paper: isDark ? designTokens.colors.gray800 : '#ffffff',
      },
      text: {
        primary: isDark ? designTokens.colors.gray50 : designTokens.colors.primary,
        secondary: isDark ? designTokens.colors.gray400 : designTokens.colors.gray500,
      },
      divider: isDark ? 'rgba(255,255,255,0.08)' : designTokens.colors.gray200,
    },
    typography: {
      fontFamily: [
        'Inter',
        '-apple-system',
        'BlinkMacSystemFont',
        '"Segoe UI"',
        'Roboto',
        '"Helvetica Neue"',
        'Arial',
        'sans-serif',
      ].join(','),
      h1: { fontWeight: 700, fontSize: '3rem', letterSpacing: '-0.02em', lineHeight: 1.25 },
      h2: { fontWeight: 700, fontSize: '2.25rem', letterSpacing: '-0.02em', lineHeight: 1.25 },
      h3: { fontWeight: 600, fontSize: '1.875rem', letterSpacing: '-0.01em', lineHeight: 1.3 },
      h4: { fontWeight: 600, fontSize: '1.5rem', letterSpacing: 0, lineHeight: 1.4 },
      h5: { fontWeight: 600, fontSize: '1.25rem', letterSpacing: 0, lineHeight: 1.4 },
      h6: { fontWeight: 600, fontSize: '1rem', letterSpacing: 0, lineHeight: 1.5 },
      body1: { fontSize: '1rem', lineHeight: 1.625 },
      body2: { fontSize: '0.875rem', lineHeight: 1.5 },
      button: { textTransform: 'none', fontWeight: 600, letterSpacing: 0 },
    },
    shape: {
      borderRadius: 12,
    },
    components: {
      MuiButton: {
        styleOverrides: {
          root: {
            borderRadius: 12,
            minHeight: 44,
            padding: '10px 20px',
            fontWeight: 600,
            transition: `all ${designTokens.duration.fast} ${designTokens.easing.out}`,
            '&:active': {
              transform: 'scale(0.97)',
            },
            '@media (prefers-reduced-motion: reduce)': {
              transition: 'none',
              '&:active': { transform: 'none' },
            },
          },
          contained: {
            background: designTokens.gradients.cta,
            boxShadow: designTokens.shadows.md,
            '&:hover': {
              boxShadow: designTokens.shadows.lg,
              transform: 'scale(1.02)',
              background: designTokens.gradients.cta,
              '@media (prefers-reduced-motion: reduce)': {
                transform: 'none',
              },
            },
          },
          outlined: {
            borderWidth: 2,
            '&:hover': {
              borderWidth: 2,
            },
          },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: 16,
            boxShadow: designTokens.shadows.md,
            border: `1px solid ${isDark ? 'rgba(255,255,255,0.06)' : designTokens.colors.gray100}`,
            transition: `all ${designTokens.duration.normal} ${designTokens.easing.out}`,
            '&:hover': {
              transform: 'translateY(-4px)',
              boxShadow: designTokens.shadows.cardHover,
              '@media (prefers-reduced-motion: reduce)': {
                transform: 'none',
              },
            },
          },
        },
      },
      MuiAppBar: {
        styleOverrides: {
          root: {
            background: 'transparent',
            boxShadow: 'none',
          },
        },
      },
      MuiDrawer: {
        styleOverrides: {
          paper: {
            borderRight: 'none',
            boxShadow: designTokens.shadows.xl,
          },
        },
      },
      MuiTextField: {
        styleOverrides: {
          root: {
            '& .MuiOutlinedInput-root': {
              borderRadius: 12,
              height: 48,
            },
          },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: {
            borderRadius: 6,
            fontWeight: 500,
          },
        },
      },
      MuiSkeleton: {
        styleOverrides: {
          root: {
            borderRadius: 8,
          },
        },
      },
      MuiPaper: {
        styleOverrides: {
          root: {
            borderRadius: 16,
          },
        },
      },
    },
    breakpoints: {
      values: {
        xs: 0,
        sm: 640,
        md: 768,
        lg: 1024,
        xl: 1280,
      },
    },
  })
}

export const lightTheme = responsiveFontSizes(buildTheme('light'))
export const darkTheme = responsiveFontSizes(buildTheme('dark'))

export const theme = lightTheme

export default theme
