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
        aria-label="User dashboard navigation"
      >
        <div class="sidenav__logo">
          <span class="sidenav__brand">My Account</span>
        </div>

        <mat-nav-list>
          <a
            mat-list-item
            routerLink="/"
            routerLinkActive="active-link"
            [routerLinkActiveOptions]="{ exact: true }"
            aria-label="Dashboard overview"
          >
            <mat-icon matListItemIcon>dashboard</mat-icon>
            <span matListItemTitle>Overview</span>
          </a>
          <a
            mat-list-item
            routerLink="/profile"
            routerLinkActive="active-link"
            aria-label="Profile"
          >
            <mat-icon matListItemIcon>person</mat-icon>
            <span matListItemTitle>Profile</span>
          </a>
          <a
            mat-list-item
            routerLink="/orders"
            routerLinkActive="active-link"
            aria-label="Order history"
          >
            <mat-icon matListItemIcon>receipt_long</mat-icon>
            <span matListItemTitle>Orders</span>
          </a>
          <a
            mat-list-item
            routerLink="/addresses"
            routerLinkActive="active-link"
            aria-label="Saved addresses"
          >
            <mat-icon matListItemIcon>location_on</mat-icon>
            <span matListItemTitle>Addresses</span>
          </a>
          <a
            mat-list-item
            routerLink="/wishlist"
            routerLinkActive="active-link"
            aria-label="Wishlist"
          >
            <mat-icon matListItemIcon>favorite</mat-icon>
            <span matListItemTitle>Wishlist</span>
          </a>
          <a
            mat-list-item
            routerLink="/payment-methods"
            routerLinkActive="active-link"
            aria-label="Payment methods"
          >
            <mat-icon matListItemIcon>credit_card</mat-icon>
            <span matListItemTitle>Payment Methods</span>
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
