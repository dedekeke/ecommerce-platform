import { type ReactNode } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { useLocation } from 'react-router-dom'

interface PageTransitionProps {
  children: ReactNode
}

const variants = {
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: -4 },
}

const reducedVariants = {
  initial: { opacity: 0 },
  animate: { opacity: 1 },
  exit: { opacity: 0 },
}

function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined') return false
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

export function PageTransition({ children }: PageTransitionProps) {
  const location = useLocation()
  const isReduced = prefersReducedMotion()

  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.div
        key={location.pathname}
        variants={isReduced ? reducedVariants : variants}
        initial="initial"
        animate="animate"
        exit="exit"
        transition={{ duration: isReduced ? 0.1 : 0.2, ease: [0.16, 1, 0.3, 1] }}
        style={{ width: '100%' }}
      >
        {children}
      </motion.div>
    </AnimatePresence>
  )
}
