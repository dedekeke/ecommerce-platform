export interface User {
  sub: string
  email: string
  email_verified?: boolean
  name?: string
  nickname?: string
  picture?: string
  updated_at?: string
}

export interface AuthState {
  token: string | null
  user: User | null
  isAuthenticated: boolean
  setAuth: (token: string, user: User) => void
  clearAuth: () => void
}

export interface CartItem {
  productId: string
  name: string
  price: number
  quantity: number
  image?: string
}

export interface CartState {
  items: CartItem[]
  total: number
  itemCount: number
  addItem: (item: Omit<CartItem, 'quantity'>) => void
  removeItem: (productId: string) => void
  updateQuantity: (productId: string, quantity: number) => void
  clearCart: () => void
}

export type NotificationType = 'success' | 'error' | 'warning' | 'info'

export interface Notification {
  id: string
  type: NotificationType
  message: string
  duration?: number
}

export interface NotificationState {
  notifications: Notification[]
  addNotification: (notification: Omit<Notification, 'id'>) => void
  removeNotification: (id: string) => void
  clearNotifications: () => void
}

export type ThemeMode = 'light' | 'dark' | 'system'
export type Language = 'en' | 'es' | 'fr' | 'de'
export type Currency = 'USD' | 'EUR' | 'GBP' | 'JPY' | 'VND' | 'CAD'

export interface UserPreferencesState {
  theme: ThemeMode
  language: Language
  currency: Currency
  setTheme: (theme: ThemeMode) => void
  setLanguage: (language: Language) => void
  setCurrency: (currency: Currency) => void
  resetPreferences: () => void
}
