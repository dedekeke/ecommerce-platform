import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/dashboard-overview/dashboard-overview.page').then(
        (m) => m.DashboardOverviewPage
      ),
  },
  {
    path: 'profile',
    loadComponent: () =>
      import('./pages/profile/profile.page').then((m) => m.ProfilePage),
  },
  {
    path: 'orders',
    loadComponent: () =>
      import('./pages/order-history/order-history.page').then((m) => m.OrderHistoryPage),
  },
  {
    path: 'orders/:id',
    loadComponent: () =>
      import('./pages/order-detail/order-detail.page').then((m) => m.OrderDetailPage),
  },
  {
    path: 'addresses',
    loadComponent: () =>
      import('./pages/addresses/addresses.page').then((m) => m.AddressesPage),
  },
  {
    path: 'wishlist',
    loadComponent: () =>
      import('./pages/wishlist/wishlist.page').then((m) => m.WishlistPage),
  },
  {
    path: 'payment-methods',
    loadComponent: () =>
      import('./pages/payment-methods/payment-methods.page').then((m) => m.PaymentMethodsPage),
  },
  {
    path: 'preferences',
    loadComponent: () =>
      import('./pages/preferences/preferences.page').then((m) => m.PreferencesPage),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
