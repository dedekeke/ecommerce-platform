import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Step from '@mui/material/Step'
import StepLabel from '@mui/material/StepLabel'
import Stepper from '@mui/material/Stepper'
import Stack from '@mui/material/Stack'
import CircularProgress from '@mui/material/CircularProgress'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import ArrowForwardIcon from '@mui/icons-material/ArrowForward'
import ShoppingBagIcon from '@mui/icons-material/ShoppingBag'
import { useTheme, useMediaQuery } from '@mui/material'

interface CheckoutStepperProps {
  steps: string[]
  activeStep: number
  onBack: () => void
  onNext: () => void
  canProceed: boolean
  isLastStep: boolean
  isFirstStep?: boolean
  isSubmitting: boolean
}

export default function CheckoutStepper({
  steps,
  activeStep,
  onBack,
  onNext,
  canProceed,
  isLastStep,
  isSubmitting,
}: CheckoutStepperProps) {
  const theme = useTheme()
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'))

  return (
    <Box>
      <Stepper
        activeStep={activeStep}
        orientation={isMobile ? 'vertical' : 'horizontal'}
        sx={{ mb: { xs: 3, md: 4 } }}
      >
        {steps.map((label) => (
          <Step key={label}>
            <StepLabel>{label}</StepLabel>
          </Step>
        ))}
      </Stepper>

      <Stack direction="row" justifyContent="space-between" sx={{ mt: 3 }}>
        <Button
          variant="outlined"
          onClick={onBack}
          disabled={activeStep === 0}
          startIcon={<ArrowBackIcon />}
          sx={{
            transition: 'all 0.2s ease',
            '&:not(:disabled):hover': { transform: 'translateX(-2px)' },
          }}
        >
          Back
        </Button>

        <Button
          variant="contained"
          onClick={onNext}
          disabled={!canProceed || isSubmitting}
          endIcon={
            isSubmitting ? (
              <CircularProgress size={16} color="inherit" />
            ) : isLastStep ? (
              <ShoppingBagIcon />
            ) : (
              <ArrowForwardIcon />
            )
          }
          sx={{
            minWidth: 160,
            py: 1.25,
            fontWeight: 600,
            transition: 'all 0.2s ease',
            '&:not(:disabled):hover': {
              transform: 'translateY(-1px)',
              boxShadow: '0 4px 12px rgba(26,26,46,0.25)',
            },
          }}
        >
          {isSubmitting ? 'Placing order...' : isLastStep ? 'Place Order' : 'Next'}
        </Button>
      </Stack>
    </Box>
  )
}
