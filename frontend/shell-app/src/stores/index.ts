// Stores
export { useAuthStore } from './authStore'
export { useCartStore } from './cartStore'
export { useNotificationStore } from './notificationStore'
export { useUserPreferencesStore } from './userPreferencesStore'

// Selectors - Auth
export {
  selectToken,
  selectUser,
  selectIsAuthenticated,
  selectUserEmail,
  selectUserName,
} from './authStore'

// Selectors - Cart
export {
  selectCartItems,
  selectCartTotal,
  selectCartItemCount,
  selectCartItem,
  selectIsInCart,
} from './cartStore'

// Selectors - Notifications
export {
  selectNotifications,
  selectNotificationById,
  selectNotificationCount,
  selectHasNotifications,
} from './notificationStore'

// Selectors - User Preferences
export {
  selectTheme,
  selectLanguage,
  selectCurrency,
  selectIsDarkMode,
  selectIsSystemTheme,
} from './userPreferencesStore'

// Types
export type {
  User,
  AuthState,
  CartItem,
  CartState,
  Notification,
  NotificationType,
  NotificationState,
  ThemeMode,
  Language,
  Currency,
  UserPreferencesState,
} from './types'
