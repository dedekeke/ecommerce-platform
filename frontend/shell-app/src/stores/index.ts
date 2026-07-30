export { useAuthStore } from './authStore'
export { useCartStore } from './cartStore'
export { useNotificationStore } from './notificationStore'
export { useUserPreferencesStore } from './userPreferencesStore'
export { useInventoryStore, selectInventoryFor } from './inventoryStore'
export type { InventoryUpdate } from './inventoryStore'

export {
  selectToken,
  selectUser,
  selectIsAuthenticated,
  selectUserEmail,
  selectUserName,
} from './authStore'

export {
  selectCartItems,
  selectCartTotal,
  selectCartItemCount,
  selectCartItem,
  selectIsInCart,
  selectPromotionCode,
  selectDiscountAmount,
  selectPromotionName,
} from './cartStore'

export {
  selectNotifications,
  selectNotificationById,
  selectNotificationCount,
  selectHasNotifications,
} from './notificationStore'

export {
  selectTheme,
  selectLanguage,
  selectCurrency,
  selectIsDarkMode,
  selectIsSystemTheme,
} from './userPreferencesStore'

export type {
  User,
  AuthState,
  CartItem,
  CartState,
  AppliedPromotion,
  Notification,
  NotificationType,
  NotificationState,
  ThemeMode,
  Language,
  Currency,
  UserPreferencesState,
} from './types'
