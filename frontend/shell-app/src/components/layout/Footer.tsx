import { Link as RouterLink } from 'react-router-dom'
import {
  Box,
  Container,
  Grid,
  Typography,
  Link,
  IconButton,
  Divider,
} from '@mui/material'
import {
  Facebook as FacebookIcon,
  Twitter as TwitterIcon,
  Instagram as InstagramIcon,
} from '@mui/icons-material'

const footerLinks = {
  customerService: [
    { label: 'Contact Us', path: '/contact' },
    { label: 'FAQ', path: '/faq' },
    { label: 'Shipping Info', path: '/shipping' },
    { label: 'Returns', path: '/returns' },
  ],
  company: [
    { label: 'About Us', path: '/about' },
    { label: 'Careers', path: '/careers' },
    { label: 'Press', path: '/press' },
  ],
  legal: [
    { label: 'Privacy Policy', path: '/privacy' },
    { label: 'Terms of Service', path: '/terms' },
    { label: 'Cookie Policy', path: '/cookies' },
  ],
}

export const Footer = () => {
  return (
    <Box
      component="footer"
      sx={{
        bgcolor: 'primary.main',
        color: 'primary.contrastText',
        py: 6,
        mt: 'auto',
      }}
    >
      <Container maxWidth="lg">
        <Grid container spacing={4}>
          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Typography variant="h6" gutterBottom fontWeight={600}>
              Customer Service
            </Typography>
            <Box component="nav">
              {footerLinks.customerService.map((link) => (
                <Link
                  key={link.path}
                  component={RouterLink}
                  to={link.path}
                  color="inherit"
                  display="block"
                  sx={{
                    mb: 1,
                    opacity: 0.8,
                    '&:hover': { opacity: 1 },
                    textDecoration: 'none',
                  }}
                >
                  {link.label}
                </Link>
              ))}
            </Box>
          </Grid>

          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Typography variant="h6" gutterBottom fontWeight={600}>
              Company
            </Typography>
            <Box component="nav">
              {footerLinks.company.map((link) => (
                <Link
                  key={link.path}
                  component={RouterLink}
                  to={link.path}
                  color="inherit"
                  display="block"
                  sx={{
                    mb: 1,
                    opacity: 0.8,
                    '&:hover': { opacity: 1 },
                    textDecoration: 'none',
                  }}
                >
                  {link.label}
                </Link>
              ))}
            </Box>
          </Grid>

          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Typography variant="h6" gutterBottom fontWeight={600}>
              Legal
            </Typography>
            <Box component="nav">
              {footerLinks.legal.map((link) => (
                <Link
                  key={link.path}
                  component={RouterLink}
                  to={link.path}
                  color="inherit"
                  display="block"
                  sx={{
                    mb: 1,
                    opacity: 0.8,
                    '&:hover': { opacity: 1 },
                    textDecoration: 'none',
                  }}
                >
                  {link.label}
                </Link>
              ))}
            </Box>
          </Grid>

          <Grid size={{ xs: 12, sm: 6, md: 3 }}>
            <Typography variant="h6" gutterBottom fontWeight={600}>
              Connect With Us
            </Typography>
            <Box>
              <IconButton
                color="inherit"
                aria-label="Facebook"
                data-testid="facebook-icon"
                sx={{ mr: 1 }}
              >
                <FacebookIcon />
              </IconButton>
              <IconButton
                color="inherit"
                aria-label="Twitter"
                data-testid="twitter-icon"
                sx={{ mr: 1 }}
              >
                <TwitterIcon />
              </IconButton>
              <IconButton
                color="inherit"
                aria-label="Instagram"
                data-testid="instagram-icon"
              >
                <InstagramIcon />
              </IconButton>
            </Box>
            <Typography variant="body2" sx={{ mt: 2, opacity: 0.8 }}>
              Subscribe to our newsletter for updates and exclusive offers.
            </Typography>
          </Grid>
        </Grid>

        <Divider sx={{ my: 4, borderColor: 'rgba(255,255,255,0.2)' }} />

        <Box
          sx={{
            display: 'flex',
            flexDirection: { xs: 'column', sm: 'row' },
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <Typography variant="body2" sx={{ opacity: 0.8 }}>
            © 2025 E-Commerce Platform. All rights reserved.
          </Typography>
          <Typography variant="body2" sx={{ opacity: 0.8, mt: { xs: 1, sm: 0 } }}>
            Built with React 19 & Spring Boot
          </Typography>
        </Box>
      </Container>
    </Box>
  )
}
