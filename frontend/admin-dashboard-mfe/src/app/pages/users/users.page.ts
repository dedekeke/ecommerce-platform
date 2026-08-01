import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { PageEvent } from '@angular/material/paginator';
import { DataTableComponent, TableColumn } from '../../shared/components/data-table/data-table.component';
import { StatusBadgeComponent, BadgeVariant } from '../../shared/components/status-badge/status-badge.component';
import { FormDrawerComponent } from '../../shared/components/form-drawer/form-drawer.component';
import { UserAdminService } from '../../core/services/user-admin.service';
import { ToastService } from '../../core/services/toast.service';
import { AdminUser, UserRole, UserStatus, UserFilterParams } from '../../core/models/user-admin.model';

type UserRow = Record<string, unknown> & AdminUser;

const USER_ROLES: UserRole[] = ['CUSTOMER', 'MODERATOR', 'ADMIN'];

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    DataTableComponent,
    StatusBadgeComponent,
    FormDrawerComponent,
  ],
  template: `
    <div class="users-page container">
      <header class="users-page__header">
        <h1>Users</h1>
      </header>

      <app-data-table
        [columns]="columns"
        [rows]="users()"
        [loading]="loading()"
        [totalElements]="totalElements()"
        [pageSize]="pageSize()"
        [pageIndex]="pageIndex()"
        emptyMessage="No users found."
        ariaLabel="Users table"
        [cellTemplate]="cellTmpl"
        [clickable]="true"
        (pageChange)="onPage($event)"
        (rowClick)="onRowClick($event)"
      />

      <ng-template #cellTmpl let-row let-col="col">
        @if (col.key === 'role') {
          <app-status-badge [label]="row['role']" [variant]="roleVariant(row['role'])" />
        } @else if (col.key === 'status') {
          <app-status-badge [label]="row['status']" [variant]="statusVariant(row['status'])" />
        } @else if (col.key === 'name') {
          {{ row['firstName'] }} {{ row['lastName'] }}
        } @else if (col.key === 'createdAt') {
          {{ row['createdAt'] | date: 'mediumDate' }}
        } @else {
          {{ row[col.key] }}
        }
      </ng-template>

      <app-form-drawer
        [title]="selectedUser() ? (selectedUser()!.firstName + ' ' + selectedUser()!.lastName) : 'User Detail'"
        [open]="drawerOpen()"
        (drawerClose)="closeDrawer()"
      >
        @if (selectedUser()) {
          <div class="user-detail">
            <div class="user-detail__row">
              <span class="user-detail__label">Email</span>
              <span>{{ selectedUser()!.email }}</span>
            </div>
            <div class="user-detail__row">
              <span class="user-detail__label">Status</span>
              <app-status-badge [label]="selectedUser()!.status" [variant]="statusVariant(selectedUser()!.status)" />
            </div>
            <div class="user-detail__row">
              <span class="user-detail__label">Joined</span>
              <span>{{ selectedUser()!.createdAt | date: 'mediumDate' }}</span>
            </div>
            @if (selectedUser()!.lastLoginAt) {
              <div class="user-detail__row">
                <span class="user-detail__label">Last Login</span>
                <span>{{ selectedUser()!.lastLoginAt | date: 'medium' }}</span>
              </div>
            }

            <h3 class="user-detail__section">Change Role</h3>
            <mat-form-field appearance="outline">
              <mat-label>Role</mat-label>
              <mat-select [value]="selectedRole()" (valueChange)="selectedRole.set($event)" data-testid="role-select">
                @for (r of roles; track r) {
                  <mat-option [value]="r">{{ r }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <button
              mat-flat-button
              color="primary"
              (click)="onUpdateRole()"
              data-testid="update-role-btn"
              [disabled]="selectedRole() === selectedUser()!.role || updatingRole()"
            >
              {{ updatingRole() ? 'Updating...' : 'Update Role' }}
            </button>
          </div>
        }
      </app-form-drawer>
    </div>
  `,
  styleUrl: './users.page.scss',
})
export class UsersPage implements OnInit {
  private readonly userService = inject(UserAdminService);
  private readonly toast = inject(ToastService);

  readonly users = signal<UserRow[]>([]);
  readonly loading = signal(true);
  readonly totalElements = signal(0);
  readonly pageSize = signal(10);
  readonly pageIndex = signal(0);
  readonly drawerOpen = signal(false);
  readonly selectedUser = signal<AdminUser | null>(null);
  readonly selectedRole = signal<UserRole>('CUSTOMER');
  readonly updatingRole = signal(false);

  readonly roles = USER_ROLES;

  readonly columns: TableColumn[] = [
    { key: 'name', label: 'Name', sortable: true },
    { key: 'email', label: 'Email' },
    { key: 'role', label: 'Role' },
    { key: 'status', label: 'Status' },
    { key: 'createdAt', label: 'Joined' },
  ];

  private currentParams: UserFilterParams = { page: 0, size: 10 };

  ngOnInit(): void {
    this.loadUsers();
  }

  private loadUsers(): void {
    this.loading.set(true);
    this.userService.getUsers(this.currentParams).subscribe({
      next: (paged) => {
        this.users.set(paged.content as UserRow[]);
        this.totalElements.set(paged.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onPage(event: PageEvent): void {
    this.currentParams = { ...this.currentParams, page: event.pageIndex, size: event.pageSize };
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadUsers();
  }

  onRowClick(row: UserRow): void {
    this.selectedUser.set(row as unknown as AdminUser);
    this.selectedRole.set(row['role'] as UserRole);
    this.drawerOpen.set(true);
  }

  closeDrawer(): void {
    this.drawerOpen.set(false);
    this.selectedUser.set(null);
  }

  onUpdateRole(): void {
    const user = this.selectedUser();
    if (!user) return;
    this.updatingRole.set(true);
    this.userService.updateUserRole(user.id, { role: this.selectedRole() }).subscribe({
      next: (updated) => {
        this.updatingRole.set(false);
        this.selectedUser.set(updated);
        this.toast.success('Role updated');
        this.loadUsers();
      },
      error: () => {
        this.updatingRole.set(false);
      },
    });
  }

  roleVariant(role: string): BadgeVariant {
    const map: Record<UserRole, BadgeVariant> = {
      ADMIN: 'error',
      MODERATOR: 'info',
      CUSTOMER: 'neutral',
    };
    return map[role as UserRole] ?? 'neutral';
  }

  statusVariant(status: string): BadgeVariant {
    const map: Record<UserStatus, BadgeVariant> = {
      ACTIVE: 'success',
      INACTIVE: 'neutral',
      SUSPENDED: 'error',
    };
    return map[status as UserStatus] ?? 'neutral';
  }
}
