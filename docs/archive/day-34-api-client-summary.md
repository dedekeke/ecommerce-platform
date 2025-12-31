# Day 34 - API Client and Interceptors

> Date: 2025-12-30

## Summary

Implemented a comprehensive API client layer with axios, including authentication interceptors, error handling, retry logic, and service classes for all backend endpoints. All implementations follow TDD methodology.

## Completed Tasks

### 1. API Client Core (`src/api/apiClient.ts`)
- Created axios instance with base configuration
  - Base URL from environment variable (`VITE_API_BASE_URL`)
  - 30-second default timeout
  - JSON content type headers
- Implemented `ApiClientError` class for structured error handling
- Created `isApiClientError` type guard function
- Added `getErrorMessage` and `getValidationErrors` utility functions

### 2. Request Interceptor
- `setupAuthInterceptor()` - Adds Auth0 bearer token to all requests
- Supports `skipAuth` config option to skip authentication for specific requests
- Graceful handling when token retrieval fails
- `removeAuthInterceptor()` for cleanup

### 3. Response Interceptor
- `setupResponseInterceptor()` - Handles error responses
- 401 callback for session expiration (triggers logout)
- 403 callback for forbidden access (redirects to home)
- Transforms all errors to `ApiClientError` instances
- `removeResponseInterceptor()` for cleanup

### 4. Request/Response Logging
- `logRequest()` - Logs outgoing requests in development mode
- `logResponse()` - Logs incoming responses in development mode
- Data truncation for large payloads (max 200 chars)
- Disabled in production builds

### 5. Retry Logic (axios-retry)
- 3 retry attempts with exponential backoff
- Retries on network errors and idempotent requests
- Retries on 429 (rate limit) and 5xx (server) errors
- Development logging of retry attempts

### 6. Service Classes

#### ProductService (`src/api/services/productService.ts`)
- `getProducts(params)` - Fetch paginated products with search/filter
- `getProductById(id)` - Fetch single product
- `getProductBySku(sku)` - Fetch product by SKU
- `searchProducts(query, params)` - Full-text search
- `getProductsByCategory(categoryId, page, size)` - Category filtering
- `getFeaturedProducts(limit)` - Featured products
- `getCategories()` - All categories
- `getCategoryById(id)` - Single category
- `getCategoryBySlug(slug)` - Category by slug
- `getSubcategories(parentId)` - Child categories

#### CartService (`src/api/services/cartService.ts`)
- `getCart()` - Fetch current user's cart
- `addItem(request)` - Add product to cart
- `updateItemQuantity(productId, request)` - Update item quantity
- `removeItem(productId)` - Remove item from cart
- `clearCart()` - Clear all items
- `getCartItemCount()` - Get total item count
- `syncCart(items)` - Sync local cart with server
- `mergeGuestCart(guestCartId)` - Merge guest cart on login

#### OrderService (`src/api/services/orderService.ts`)
- `getOrders(params)` - Fetch paginated orders
- `getOrderById(id)` - Fetch single order
- `getOrderByNumber(orderNumber)` - Fetch by order number
- `createOrder(request)` - Create new order
- `cancelOrder(id)` - Cancel order
- `trackOrder(id)` - Get tracking information
- `validatePromotion(request)` - Validate promotion code
- `reorder(orderId)` - Reorder from previous order

#### UserService (`src/api/services/userService.ts`)
- `getCurrentUser()` - Fetch current user profile
- `updateProfile(request)` - Update profile
- `getAddresses()` - Fetch all addresses
- `getAddressById(id)` - Fetch single address
- `createAddress(request)` - Create new address
- `updateAddress(id, request)` - Update address
- `deleteAddress(id)` - Delete address
- `setDefaultAddress(id)` - Set default address
- `getWishlist()` - Fetch wishlist product IDs
- `addToWishlist(productId)` - Add to wishlist
- `removeFromWishlist(productId)` - Remove from wishlist
- `isInWishlist(productId)` - Check if in wishlist

### 7. Error Handling Utilities (`src/api/errorHandling.ts`)
- `getHttpErrorMessage(status)` - User-friendly HTTP error messages
- `handleApiError(error)` - Extract error message from any error
- `createErrorHandler(options)` - Create configurable error handler
- Type check functions:
  - `isNetworkError(error)`
  - `isServerError(error)`
  - `isClientError(error)`
  - `isUnauthorizedError(error)`
  - `isForbiddenError(error)`
  - `isNotFoundError(error)`
  - `isValidationError(error)`
- Notification functions:
  - `showErrorNotification(message, duration)`
  - `showSuccessNotification(message, duration)`
  - `showWarningNotification(message, duration)`
  - `showInfoNotification(message, duration)`

### 8. API Setup Hook (`src/hooks/useApiSetup.ts`)
- Integrates Auth0 with API interceptors
- Sets up auth interceptor with token retrieval
- Sets up response interceptor with error callbacks
- Handles session expiration (logout + notification)
- Handles forbidden access (redirect + notification)
- Automatic cleanup on unmount

### 9. API Types (`src/api/types.ts`)
- `ApiResponse<T>` - Generic API response wrapper
- `PaginatedResponse<T>` - Paginated list response
- `ApiError` - Error response structure
- `Product`, `Category`, `ProductSearchParams`
- `CartResponse`, `CartItemResponse`, `AddToCartRequest`, `UpdateCartItemRequest`
- `Order`, `OrderItem`, `OrderStatus`, `CreateOrderRequest`, `OrderSearchParams`
- `UserProfile`, `Address`, `UpdateUserProfileRequest`, `CreateAddressRequest`, `UpdateAddressRequest`
- `Promotion`, `PromotionType`, `ValidatePromotionRequest`, `ValidatePromotionResponse`

## Test Coverage

| File | Tests |
|------|-------|
| apiClient.test.ts | 29 |
| errorHandling.test.ts | 41 |
| productService.test.ts | 16 |
| cartService.test.ts | 10 |
| orderService.test.ts | 11 |
| userService.test.ts | 15 |
| useApiSetup.test.ts | 8 |
| **Total API Tests** | **130** |

**Overall Project Tests: 234 passing**

## File Structure

```
frontend/shell-app/src/
├── api/
│   ├── index.ts              # Main exports
│   ├── apiClient.ts          # Axios instance and interceptors
│   ├── apiClient.test.ts
│   ├── errorHandling.ts      # Error handling utilities
│   ├── errorHandling.test.ts
│   ├── types.ts              # API type definitions
│   └── services/
│       ├── index.ts          # Service exports
│       ├── productService.ts
│       ├── productService.test.ts
│       ├── cartService.ts
│       ├── cartService.test.ts
│       ├── orderService.ts
│       ├── orderService.test.ts
│       ├── userService.ts
│       └── userService.test.ts
└── hooks/
    ├── index.ts              # Updated with useApiSetup
    ├── useApiSetup.ts        # API + Auth0 integration hook
    └── useApiSetup.test.ts
```

## Dependencies Added

```json
{
  "axios": "^1.7.x",
  "axios-retry": "^4.x"
}
```

## Usage Examples

### Basic API Call
```typescript
import { productService } from '@/api';

const products = await productService.getProducts({
  categoryId: 'electronics',
  page: 0,
  size: 20,
});
```

### With Error Handling
```typescript
import { productService, handleApiError, showErrorNotification } from '@/api';

try {
  const product = await productService.getProductById('prod-123');
} catch (error) {
  const message = handleApiError(error);
  showErrorNotification(message);
}
```

### Using Error Handler Factory
```typescript
import { cartService, createErrorHandler } from '@/api';

const errorHandler = createErrorHandler({
  showNotification: true,
  onUnauthorized: () => navigate('/login'),
  onValidationError: (errors) => setFormErrors(errors),
});

try {
  await cartService.addItem({ productId: 'prod-123', quantity: 1 });
} catch (error) {
  errorHandler(error);
}
```

### API Setup in App Root
```typescript
import { useApiSetup } from '@/hooks';

function AppContent() {
  useApiSetup(); // Sets up interceptors with Auth0

  return <Routes>...</Routes>;
}
```

## Next Steps (Day 35)

1. **Micro-Frontend Loading Infrastructure**
   - Create `MicroFrontendLoader` component with React.lazy and Suspense
   - Implement dynamic module loading with error handling
   - Create loading fallback UI (skeleton screens)
   - Create error fallback UI (retry button)
   - Create error boundaries specific to micro-frontends
   - Implement module preloading strategy (on hover)
   - Create micro-frontend registry configuration
   - Test module loading/unloading/error scenarios
   - Implement fallback to cached version if remote fails

## Notes

- All services use consistent error handling via `ApiClientError`
- Request logging only enabled in development (`import.meta.env.DEV`)
- Retry logic prevents transient failures from affecting UX
- Type safety enforced throughout with TypeScript
- Tests mock axios to avoid actual network calls
- Auth0 integration seamlessly adds tokens to all API requests
