import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

export interface ToastEventDetail {
  type: ToastType;
  message: string;
  duration?: number;
}

declare global {
  interface Window {
    __ecommerceToastHost?: boolean;
  }
}

const DEFAULT_DURATION = 3000;

/**
 * Dispatches toasts to the shell app's global toast host when mounted
 * (window.__ecommerceToastHost === true), so notifications render with the
 * shell's shared UI. Falls back to MatSnackBar for standalone `ng serve`.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string, duration?: number): void {
    this.show('success', message, duration);
  }

  error(message: string, duration?: number): void {
    this.show('error', message, duration);
  }

  warning(message: string, duration?: number): void {
    this.show('warning', message, duration);
  }

  info(message: string, duration?: number): void {
    this.show('info', message, duration);
  }

  private show(type: ToastType, message: string, duration?: number): void {
    if (window.__ecommerceToastHost === true) {
      const detail: ToastEventDetail = { type, message, duration };
      window.dispatchEvent(new CustomEvent<ToastEventDetail>('ecommerce:toast', { detail }));
      return;
    }
    this.snackBar.open(message, 'Close', { duration: duration ?? DEFAULT_DURATION });
  }
}
