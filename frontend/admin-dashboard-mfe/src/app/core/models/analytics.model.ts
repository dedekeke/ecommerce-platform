export interface RevenueDataPoint {
  date: string;
  revenue: number;
  orderCount: number;
}

export interface CategoryRevenue {
  category: string;
  revenue: number;
  percentage: number;
}

export interface KpiSummary {
  todayOrders: number;
  todayOrdersDelta: number;
  todayRevenue: number;
  todayRevenueDelta: number;
  lowStockItems: number;
  pendingApprovals: number;
}

export interface ActivityEvent {
  id: string;
  type: 'ORDER_PLACED' | 'USER_REGISTERED' | 'PRODUCT_UPDATED' | 'PAYMENT_FAILED' | 'ORDER_SHIPPED';
  description: string;
  timestamp: string;
  entityId?: string;
}
