# Frontend Design System & UI/UX Guidelines

> Back to [README](../README.md).
>
> **Purpose**: This document defines the design language, visual standards, and UI/UX patterns for all frontend applications and micro-frontends in the e-commerce platform.
>
> **Last Updated**: 2025-12-30

---

## Design Philosophy

You are an **expert UI/UX designer** creating a modern e-commerce platform. The design should embody:

| Aspect | Direction |
|--------|-----------|
| **Primary Style** | Clean, minimal, Apple-inspired with generous whitespace |
| **Accent Style** | Playful micro-interactions and subtle humor (memecoin energy) |
| **Overall Vibe** | "Professional but doesn't take itself too seriously" |

### Core Principles

1. **Clarity Over Cleverness** - Users should never be confused
2. **Delight in Details** - Micro-interactions that make users smile
3. **Performance is UX** - Fast is beautiful
4. **Accessibility First** - Beautiful for everyone
5. **Mobile-First** - Design for thumbs, scale to desktops

---

## Design Tokens

### Color Palette

```css
/* Primary Colors */
--color-primary: #0A0A0A;        /* Near-black - text, headers */
--color-secondary: #FAFAFA;      /* Off-white - backgrounds */
--color-accent: #6366F1;         /* Indigo-500 - CTAs, links */
--color-accent-hover: #4F46E5;   /* Indigo-600 - hover states */

/* Semantic Colors */
--color-success: #10B981;        /* Emerald-500 */
--color-warning: #F59E0B;        /* Amber-500 */
--color-error: #EF4444;          /* Red-500 */
--color-info: #3B82F6;           /* Blue-500 */

/* Neutral Scale */
--color-gray-50: #FAFAFA;
--color-gray-100: #F4F4F5;
--color-gray-200: #E4E4E7;
--color-gray-300: #D4D4D8;
--color-gray-400: #A1A1AA;
--color-gray-500: #71717A;       /* Muted text */
--color-gray-600: #52525B;
--color-gray-700: #3F3F46;
--color-gray-800: #27272A;
--color-gray-900: #18181B;

/* Gradients */
--gradient-hero: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
--gradient-cta: linear-gradient(90deg, #6366F1 0%, #8B5CF6 100%);
--gradient-success: linear-gradient(90deg, #10B981 0%, #34D399 100%);
--gradient-premium: linear-gradient(135deg, #F59E0B 0%, #EF4444 100%);

/* Glass Effect */
--glass-bg: rgba(255, 255, 255, 0.8);
--glass-blur: blur(20px);
--glass-border: 1px solid rgba(255, 255, 255, 0.2);
```

### Typography

```css
/* Font Families */
--font-primary: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
--font-mono: 'JetBrains Mono', 'Fira Code', monospace;

/* Font Sizes */
--text-xs: 0.75rem;      /* 12px */
--text-sm: 0.875rem;     /* 14px */
--text-base: 1rem;       /* 16px */
--text-lg: 1.125rem;     /* 18px */
--text-xl: 1.25rem;      /* 20px */
--text-2xl: 1.5rem;      /* 24px */
--text-3xl: 1.875rem;    /* 30px */
--text-4xl: 2.25rem;     /* 36px */
--text-5xl: 3rem;        /* 48px */
--text-6xl: 3.75rem;     /* 60px */
--text-7xl: 4.5rem;      /* 72px */
--text-8xl: 6rem;        /* 96px */

/* Line Heights */
--leading-tight: 1.25;
--leading-normal: 1.5;
--leading-relaxed: 1.625;

/* Letter Spacing */
--tracking-tight: -0.02em;
--tracking-normal: 0;
--tracking-wide: 0.1em;
```

#### Typography Scale

| Element | Size | Weight | Letter Spacing | Usage |
|---------|------|--------|----------------|-------|
| Hero | 72-96px | 700 | -0.02em | Landing page headlines |
| H1 | 48-64px | 700 | -0.02em | Page titles |
| H2 | 36-48px | 600 | -0.01em | Section headers |
| H3 | 24-30px | 600 | 0 | Card titles, subsections |
| H4 | 20-24px | 600 | 0 | Small headers |
| Body | 16-18px | 400 | 0 | Paragraphs, descriptions |
| Small | 14px | 400 | 0 | Secondary text, metadata |
| Caption | 12px | 500 | 0.1em (uppercase) | Labels, badges |
| Price | 24-32px | 700 | -0.01em | Product prices (use mono font) |

### Spacing System (8px Base)

```css
--space-0: 0;
--space-1: 0.25rem;   /* 4px */
--space-2: 0.5rem;    /* 8px */
--space-3: 0.75rem;   /* 12px */
--space-4: 1rem;      /* 16px */
--space-5: 1.25rem;   /* 20px */
--space-6: 1.5rem;    /* 24px */
--space-8: 2rem;      /* 32px */
--space-10: 2.5rem;   /* 40px */
--space-12: 3rem;     /* 48px */
--space-16: 4rem;     /* 64px */
--space-20: 5rem;     /* 80px */
--space-24: 6rem;     /* 96px */
--space-32: 8rem;     /* 128px */
```

### Border Radius

```css
--radius-sm: 6px;
--radius-md: 12px;
--radius-lg: 16px;
--radius-xl: 24px;
--radius-2xl: 32px;
--radius-full: 9999px;
```

### Shadows

```css
--shadow-sm: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
--shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -2px rgba(0, 0, 0, 0.1);
--shadow-lg: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -4px rgba(0, 0, 0, 0.1);
--shadow-xl: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
--shadow-glow: 0 0 20px rgba(99, 102, 241, 0.3);
--shadow-card-hover: 0 20px 40px -10px rgba(0, 0, 0, 0.15);
```

---

## Component Styling Guidelines

### Buttons

```css
/* Base Button */
.btn {
  min-height: 48px;
  padding: 12px 24px;
  border-radius: var(--radius-md);
  font-weight: 600;
  font-size: var(--text-base);
  transition: all 0.15s ease-out;
  cursor: pointer;
}

/* Primary Button */
.btn-primary {
  background: var(--gradient-cta);
  color: white;
  box-shadow: var(--shadow-md);
}
.btn-primary:hover {
  transform: scale(1.02);
  box-shadow: var(--shadow-lg);
}
.btn-primary:active {
  transform: scale(0.98);
}

/* Secondary/Ghost Button */
.btn-secondary {
  background: transparent;
  border: 2px solid var(--color-gray-300);
  color: var(--color-primary);
}
.btn-secondary:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}
```

**Button Variants:**
- **Primary**: Solid gradient background, used for main CTAs
- **Secondary**: Ghost/outline style, used for secondary actions
- **Tertiary**: Text-only with underline on hover
- **Destructive**: Red background for delete/cancel actions
- **Icon Button**: Square aspect ratio, icon only

### Cards

```css
.card {
  background: white;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-gray-100);
  box-shadow: var(--shadow-md);
  padding: var(--space-6);
  transition: all 0.2s ease-out;
}

.card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-card-hover);
}

/* Product Card Specific */
.product-card {
  overflow: hidden;
  padding: 0;
}
.product-card__image {
  aspect-ratio: 1;
  object-fit: cover;
}
.product-card__content {
  padding: var(--space-4);
}
```

### Form Inputs

```css
.input {
  height: 48px;
  padding: 12px 16px;
  border: 2px solid var(--color-gray-200);
  border-radius: var(--radius-md);
  font-size: var(--text-base);
  transition: all 0.15s ease-out;
  width: 100%;
}

.input:focus {
  outline: none;
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.input:invalid:not(:placeholder-shown) {
  border-color: var(--color-error);
}

.input-label {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--color-gray-700);
  margin-bottom: var(--space-2);
  display: block;
}
```

### Navigation

```css
/* Sticky Header with Glass Effect */
.header {
  position: sticky;
  top: 0;
  z-index: 50;
  height: 72px;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  border-bottom: var(--glass-border);
}

/* Nav Links */
.nav-link {
  font-weight: 500;
  color: var(--color-gray-600);
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-md);
  transition: all 0.15s ease-out;
}

.nav-link:hover {
  color: var(--color-primary);
  background: var(--color-gray-100);
}

.nav-link.active {
  color: var(--color-accent);
}
```

---

## Animation Guidelines

### Transition Presets

```css
/* Timing Functions */
--ease-out: cubic-bezier(0.16, 1, 0.3, 1);
--ease-bounce: cubic-bezier(0.34, 1.56, 0.64, 1);
--ease-spring: cubic-bezier(0.22, 1, 0.36, 1);

/* Durations */
--duration-fast: 0.15s;
--duration-normal: 0.2s;
--duration-slow: 0.3s;
--duration-page: 0.5s;
```

### Micro-Interactions (The Fun Part)

| Interaction | Animation | Description |
|-------------|-----------|-------------|
| Button Hover | `scale(1.02)` + shadow lift | Subtle "pop" effect |
| Button Click | `scale(0.98)` | Satisfying press feedback |
| Card Hover | `translateY(-4px)` + shadow | Gentle float upward |
| Add to Cart | Item "flies" to cart icon | Visual confirmation |
| Success Action | Confetti burst OR checkmark animation | Celebration moment |
| Like/Favorite | Heart pulse + color fill | Immediate gratification |
| Toggle Switch | Smooth slide with bounce | Playful state change |
| Skeleton Loading | Shimmer wave effect | Content anticipation |

### Scroll Animations

```css
/* Fade In Up on Scroll */
.fade-in-up {
  opacity: 0;
  transform: translateY(20px);
  transition: all 0.6s var(--ease-out);
}
.fade-in-up.visible {
  opacity: 1;
  transform: translateY(0);
}

/* Stagger Children */
.stagger-children > * {
  opacity: 0;
  transform: translateY(10px);
}
.stagger-children.visible > *:nth-child(1) { transition-delay: 0ms; }
.stagger-children.visible > *:nth-child(2) { transition-delay: 50ms; }
.stagger-children.visible > *:nth-child(3) { transition-delay: 100ms; }
/* ... continue as needed */
```

### Loading States

1. **Skeleton Screens**: Use shimmer animation for content placeholders
2. **Spinners**: Minimal, use only for short operations (<1s)
3. **Progress Bars**: For operations with known duration
4. **Optimistic UI**: Update immediately, rollback on error

---

## Layout Principles

### Grid System

```css
/* Container */
.container {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 var(--space-4);
}

@media (min-width: 768px) {
  .container {
    padding: 0 var(--space-8);
  }
}

@media (min-width: 1280px) {
  .container {
    padding: 0 var(--space-16);
  }
}

/* Grid */
.grid {
  display: grid;
  gap: var(--space-6);
}

/* Product Grid */
.product-grid {
  grid-template-columns: repeat(2, 1fr);
}

@media (min-width: 768px) {
  .product-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (min-width: 1024px) {
  .product-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}
```

### Whitespace Philosophy

> "When in doubt, add more space."

| Element | Vertical Padding |
|---------|------------------|
| Hero Section | 120px+ (desktop), 80px (mobile) |
| Content Sections | 96px (desktop), 64px (mobile) |
| Cards Internal | 24px |
| Between Cards | 24-32px |
| Form Fields | 16px vertical gap |

### Responsive Breakpoints

```css
/* Mobile First Approach */
--breakpoint-sm: 640px;   /* Mobile landscape */
--breakpoint-md: 768px;   /* Tablet */
--breakpoint-lg: 1024px;  /* Laptop */
--breakpoint-xl: 1280px;  /* Desktop */
--breakpoint-2xl: 1536px; /* Large desktop */
```

---

## Page-Specific Guidelines

### Landing/Home Page

**Hero Section:**
- Full viewport height (100vh)
- Bold, single-line headline with gradient or animated text
- One clear CTA button
- Optional: Floating product showcase or 3D element
- Subtle background animation (gradient shift, particles, or parallax)

**Social Proof:**
- Logo carousel of trusted brands/partners
- Customer testimonials with photos
- Review score highlights

**Product Showcase:**
- Featured products in grid (4 items)
- Hover reveals quick-add button
- "View All" link to catalog

### Product Listing Page

**Layout:**
- Filters sidebar (desktop) or drawer (mobile)
- 4-column grid (desktop), 2-column (mobile)
- Infinite scroll OR "Load More" button with animation
- Sort dropdown (popularity, price, newest)

**Product Cards:**
- Square image (1:1 aspect ratio)
- Product name (2 lines max, truncate)
- Price (bold, prominent)
- Rating stars
- Quick-add button on hover
- Wishlist heart icon

### Product Detail Page

**Layout:**
- Split view: sticky images (left), scrolling details (right)
- Image gallery with zoom on hover
- Thumbnail strip below main image

**Content Hierarchy:**
1. Brand name (small, muted)
2. Product name (H1)
3. Rating + review count
4. Price (large, mono font)
5. Variant selectors (color, size)
6. Quantity selector
7. Add to Cart button (full width, primary)
8. Add to Wishlist (secondary)
9. Description accordion
10. Specifications accordion
11. Reviews section

### Cart Page

**Layout:**
- Two-column: items list (left), summary (right)
- Mobile: stacked layout with sticky summary

**Cart Item:**
- Product image (square)
- Name + variant info
- Quantity controls (-, number, +)
- Line total
- Remove button (subtle, appears on hover)

**Order Summary:**
- Subtotal
- Shipping estimate
- Tax estimate
- Promo code input
- Total (bold, large)
- Checkout button (primary, full width)
- Trust badges (secure checkout, free returns)

### Checkout Flow

**Progress Indicator:**
- Stepper component: Shipping → Payment → Review → Confirmation
- Current step highlighted, completed steps with checkmark

**Form Design:**
- One column layout
- Clear section headers
- Inline validation
- Auto-format inputs (phone, card number)
- Address autocomplete

**Confirmation Page:**
- Success animation (checkmark or confetti)
- Order number (large, copyable)
- Order summary
- Estimated delivery
- "Continue Shopping" button

---

## Copy/Voice Guidelines (The Personality)

### Tone

| Context | Tone | Example |
|---------|------|---------|
| Error Messages | Helpful, not robotic | "Oops! That didn't work. Let's try again?" |
| Empty States | Friendly, encouraging | "Your cart is feeling lonely" |
| Success States | Celebratory | "You did it! Order confirmed." |
| Loading | Patient, optimistic | "Good things take time..." |
| 404 | Playful, helpful | "This page went on vacation without telling us." |

### Microcopy Examples

```
// Empty Cart
"Your cart is feeling lonely"
[Continue Shopping button]

// Empty Wishlist
"No favorites yet. Start your collection!"
[Browse Products button]

// No Search Results
"We couldn't find that. Maybe try a different search?"

// Successful Add to Cart
"Nice choice! Added to your cart"

// Out of Stock
"Sold out! Join the waitlist and we'll let you know when it's back."

// Form Validation
Email: "That doesn't look like an email address"
Password: "Add a few more characters to make it strong"
Required: "This field needs some love"

// Loading States
"Fetching the good stuff..."
"Almost there..."
"Preparing something beautiful..."
```

---

## Tech Stack Preferences

### Core Technologies

| Tool | Purpose |
|------|---------|
| **Tailwind CSS** | Utility-first styling |
| **Framer Motion** | React animations |
| **Heroicons / Lucide** | Icon library |
| **Headless UI** | Accessible components |
| **React Hook Form** | Form management |
| **Zod** | Validation |

### Animation Libraries

- **Framer Motion**: Complex animations, page transitions
- **CSS Transitions**: Simple hover states, micro-interactions
- **Lottie**: Complex illustrations, loading animations
- **GSAP** (optional): Scroll-triggered animations, timelines

### Image Optimization

- Use `next/image` or equivalent for automatic optimization
- Serve WebP with fallbacks
- Implement lazy loading with blur placeholders
- Use aspect-ratio CSS to prevent layout shift

---

## Quality Checklist

### Before Each Component/Page

- [ ] Consistent spacing using design tokens
- [ ] Hover/focus states on ALL interactive elements
- [ ] Loading and error states for async operations
- [ ] Mobile-first responsive design tested
- [ ] Accessible (keyboard navigation, screen reader)
- [ ] Smooth animations (60fps, no jank)
- [ ] Dark mode support (if enabled)

### Accessibility (WCAG 2.1 AA)

- [ ] Color contrast ratio ≥ 4.5:1 for text
- [ ] Focus indicators visible
- [ ] Interactive elements ≥ 44x44px touch target
- [ ] Alt text for images
- [ ] ARIA labels where needed
- [ ] Keyboard navigable
- [ ] Reduced motion preference respected

### Performance

- [ ] Lighthouse score > 90
- [ ] First Contentful Paint < 1.5s
- [ ] Time to Interactive < 3s
- [ ] Cumulative Layout Shift < 0.1
- [ ] No layout shifts during image load

---

## Using This Design System

### With Claude Frontend Skill

Invoke the skill and reference this document:

```
/frontend-design

Create a product card component following the design brief in
docs/frontend-design-brief.md. Include:
- Image with hover zoom effect
- Product name, price, and rating
- Quick-add-to-cart button on hover
- Wishlist heart icon
- Proper loading skeleton
```

### Example Prompts

**Hero Section:**
```
/frontend-design

Build a stunning hero section for the e-commerce homepage following
our design brief. Include:
- Gradient animated background
- Bold headline with typing effect
- Single CTA button with hover animation
- Floating product images with parallax
```

**Product Grid:**
```
/frontend-design

Create a responsive product grid component following our design system.
Features:
- 4 columns desktop, 2 mobile
- Staggered fade-in animation on scroll
- Filter sidebar that becomes drawer on mobile
- Skeleton loading states
```

**Checkout Flow:**
```
/frontend-design

Design a multi-step checkout flow with:
- Progress stepper (Shipping → Payment → Review)
- Form validation with inline errors
- Order summary sidebar
- Success confirmation with confetti animation
```

---

## Design Assets & References

### Inspiration Sources

- **Apple.com**: Clean layouts, whitespace, typography
- **Stripe**: Forms, micro-interactions, developer UX
- **Linear**: Animations, dark mode, polish
- **Vercel**: Marketing pages, gradients
- **Memecoin sites**: Bold typography, playful animations, character

### Font Resources

- [Inter](https://fonts.google.com/specimen/Inter) - Primary font
- [JetBrains Mono](https://www.jetbrains.com/lp/mono/) - Code/prices

### Icon Libraries

- [Heroicons](https://heroicons.com/) - Primary icons
- [Lucide](https://lucide.dev/) - Alternative icons
- [Tabler Icons](https://tabler-icons.io/) - Extended set

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-12-30 | Initial design system |

---

> **Remember**: Great design is invisible. Users should never think about the interface—they should just *use* it and enjoy it.
