import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/admin-overview/admin-overview.page').then((m) => m.AdminOverviewPage),
  },
  {
    path: 'products',
    loadComponent: () =>
      import('./pages/products/products.page').then((m) => m.ProductsPage),
  },
  {
    path: 'products/:id',
    loadComponent: () =>
      import('./pages/product-detail/product-detail.page').then((m) => m.ProductDetailPage),
  },
  {
    path: 'orders',
    loadComponent: () =>
      import('./pages/orders-admin/orders-admin.page').then((m) => m.OrdersAdminPage),
  },
  {
    path: 'users',
    loadComponent: () =>
      import('./pages/users/users.page').then((m) => m.UsersPage),
  },
  {
    path: 'refunds',
    loadComponent: () =>
      import('./pages/refunds-admin/refunds-admin.page').then((m) => m.RefundsAdminPage),
  },
  {
    path: 'returns',
    loadComponent: () =>
      import('./pages/returns-admin/returns-admin.page').then((m) => m.ReturnsAdminPage),
  },
  {
    path: 'analytics',
    loadComponent: () =>
      import('./pages/analytics/analytics.page').then((m) => m.AnalyticsPage),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
