import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import NProgress from 'nprogress'

NProgress.configure({
  showSpinner: false,
  trickleSpeed: 200,
  minimum: 0.08,
})

const PROGRESS_BAR_CSS = `
#nprogress {
  pointer-events: none;
}
#nprogress .bar {
  background: linear-gradient(90deg, #6366F1 0%, #8B5CF6 100%);
  position: fixed;
  z-index: 9999;
  top: 0;
  left: 0;
  width: 100%;
  height: 3px;
  border-radius: 0 2px 2px 0;
}
#nprogress .peg {
  display: block;
  position: absolute;
  right: 0px;
  width: 100px;
  height: 100%;
  box-shadow: 0 0 10px #6366F1, 0 0 5px #6366F1;
  opacity: 1;
  transform: rotate(3deg) translate(0px, -4px);
}
@media (prefers-reduced-motion: reduce) {
  #nprogress .bar { transition: none !important; }
}
`

export function RouteProgressBar() {
  const location = useLocation()

  useEffect(() => {
    const style = document.createElement('style')
    style.textContent = PROGRESS_BAR_CSS
    document.head.appendChild(style)
    return () => {
      document.head.removeChild(style)
    }
  }, [])

  useEffect(() => {
    NProgress.start()
    const timer = setTimeout(() => {
      NProgress.done()
    }, 300)
    return () => {
      clearTimeout(timer)
      NProgress.done()
    }
  }, [location.pathname])

  return null
}
