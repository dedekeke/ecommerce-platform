// API Client
export {
  apiClient,
  ApiClientError,
  isApiClientError,
  setupAuthInterceptor,
  setupResponseInterceptor,
  removeAuthInterceptor,
  removeResponseInterceptor,
  getErrorMessage,
  getValidationErrors,
  logRequest,
  logResponse,
} from './apiClient'

// Error Handling
export {
  handleApiError,
  createErrorHandler,
  getHttpErrorMessage,
  isNetworkError,
  isServerError,
  isClientError,
  isUnauthorizedError,
  isForbiddenError,
  isNotFoundError,
  isValidationError,
  showErrorNotification,
  showSuccessNotification,
  showWarningNotification,
  showInfoNotification,
  type ErrorHandlerOptions,
} from './errorHandling'

// Services
export {
  productService,
  cartService,
  orderService,
  userService,
  type OrderTrackingInfo,
} from './services'

// Types
export type {
  ApiResponse,
  PaginatedResponse,
  ApiError,
  Product,
  Category,
  ProductSearchParams,
  CartResponse,
  CartItemResponse,
  AddToCartRequest,
  UpdateCartItemRequest,
  Order,
  OrderItem,
  OrderStatus,
  CreateOrderRequest,
  OrderSearchParams,
  UserProfile,
  Address,
  UpdateUserProfileRequest,
  CreateAddressRequest,
  UpdateAddressRequest,
  Promotion,
  PromotionType,
  ValidatePromotionRequest,
  ValidatePromotionResponse,
} from './types'
