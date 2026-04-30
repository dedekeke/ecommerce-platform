import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatIconModule,
    MatSidenavModule,
    MatListModule,
  ],
  template: `
    <mat-sidenav-container class="sidenav-container">
      <mat-sidenav
        mode="side"
        [opened]="true"
        class="sidenav"
        role="navigation"
        aria-label="Admin dashboard navigation"
      >
        <div class="sidenav__logo">
          <span class="sidenav__brand">Admin</span>
        </div>

        <mat-nav-list>
          <a
            mat-list-item
            routerLink="/"
            routerLinkActive="active-link"
            [routerLinkActiveOptions]="{ exact: true }"
            aria-label="Admin overview"
          >
            <mat-icon matListItemIcon>dashboard</mat-icon>
            <span matListItemTitle>Overview</span>
          </a>
          <a
            mat-list-item
            routerLink="/products"
            routerLinkActive="active-link"
            aria-label="Products"
          >
            <mat-icon matListItemIcon>inventory_2</mat-icon>
            <span matListItemTitle>Products</span>
          </a>
          <a
            mat-list-item
            routerLink="/orders"
            routerLinkActive="active-link"
            aria-label="Orders"
          >
            <mat-icon matListItemIcon>receipt_long</mat-icon>
            <span matListItemTitle>Orders</span>
          </a>
          <a
            mat-list-item
            routerLink="/users"
            routerLinkActive="active-link"
            aria-label="Users"
          >
            <mat-icon matListItemIcon>people</mat-icon>
            <span matListItemTitle>Users</span>
          </a>
          <a
            mat-list-item
            routerLink="/analytics"
            routerLinkActive="active-link"
            aria-label="Analytics"
          >
            <mat-icon matListItemIcon>bar_chart</mat-icon>
            <span matListItemTitle>Analytics</span>
          </a>
        </mat-nav-list>
      </mat-sidenav>

      <mat-sidenav-content class="main-content">
        <router-outlet />
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styleUrl: './app.component.scss',
})
export class AppComponent {}
