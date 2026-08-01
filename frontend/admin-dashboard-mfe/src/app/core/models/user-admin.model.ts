export type UserRole = 'CUSTOMER' | 'ADMIN' | 'MODERATOR';

export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';

export interface AdminUser {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  role: UserRole;
  status: UserStatus;
  createdAt: string;
  lastLoginAt?: string;
  orderCount?: number;
}

export interface PagedUsers {
  content: AdminUser[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface UserFilterParams {
  page: number;
  size: number;
  search?: string;
  role?: UserRole;
  status?: UserStatus;
}

export interface UpdateUserRolePayload {
  role: UserRole;
}
