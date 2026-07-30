export type ThemePreference = 'light' | 'dark' | 'system';

export type NotificationCategory = 'orderUpdates' | 'promotions' | 'wishlistRestock' | 'security';

export const NOTIFICATION_CATEGORIES: NotificationCategory[] = [
  'orderUpdates',
  'promotions',
  'wishlistRestock',
  'security',
];

export const NOTIFICATION_CATEGORY_LABELS: Record<NotificationCategory, string> = {
  orderUpdates: 'Order Updates',
  promotions: 'Promotions & Deals',
  wishlistRestock: 'Wishlist Restock Alerts',
  security: 'Security Alerts',
};

export interface NotificationChannels {
  email: boolean;
  sms: boolean;
  push: boolean;
}

export type NotificationSettings = Record<NotificationCategory, NotificationChannels>;

export interface DisplayPreferences {
  theme: ThemePreference;
  language: string;
  currency: string;
}

export interface EmailSubscriptions {
  newsletter: boolean;
  productAnnouncements: boolean;
  surveys: boolean;
}

export interface UserPreferences {
  notifications: NotificationSettings;
  display: DisplayPreferences;
  emailSubscriptions: EmailSubscriptions;
}

export const DEFAULT_PREFERENCES: UserPreferences = {
  notifications: {
    orderUpdates: { email: true, sms: false, push: true },
    promotions: { email: true, sms: false, push: false },
    wishlistRestock: { email: true, sms: false, push: false },
    security: { email: true, sms: true, push: true },
  },
  display: { theme: 'system', language: 'en', currency: 'USD' },
  emailSubscriptions: { newsletter: true, productAnnouncements: false, surveys: false },
};

export const SUPPORTED_LANGUAGES: ReadonlyArray<{ value: string; label: string }> = [
  { value: 'en', label: 'English' },
  { value: 'es', label: 'Español' },
  { value: 'fr', label: 'Français' },
  { value: 'de', label: 'Deutsch' },
];

export const SUPPORTED_CURRENCIES: ReadonlyArray<{ value: string; label: string }> = [
  { value: 'USD', label: 'USD ($)' },
  { value: 'EUR', label: 'EUR (€)' },
  { value: 'GBP', label: 'GBP (£)' },
];
