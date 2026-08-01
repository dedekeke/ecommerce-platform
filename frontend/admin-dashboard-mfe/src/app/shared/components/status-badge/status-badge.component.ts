import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatChipsModule } from '@angular/material/chips';

export type BadgeVariant = 'success' | 'warning' | 'error' | 'info' | 'neutral';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [CommonModule, MatChipsModule],
  template: `
    <span
      class="status-badge"
      [class]="'status-badge--' + variant()"
      [attr.aria-label]="'Status: ' + label()"
    >{{ label() }}</span>
  `,
  styles: [`
    .status-badge {
      display: inline-flex;
      align-items: center;
      padding: 2px 10px;
      border-radius: var(--radius-full, 9999px);
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.04em;

      &--success { background: rgba(16, 185, 129, 0.12); color: #059669; }
      &--warning { background: rgba(245, 158, 11, 0.12); color: #d97706; }
      &--error   { background: rgba(239, 68, 68, 0.12);  color: #dc2626; }
      &--info    { background: rgba(59, 130, 246, 0.12); color: #2563eb; }
      &--neutral { background: rgba(113, 113, 122, 0.12); color: #52525b; }
    }
  `],
})
export class StatusBadgeComponent {
  readonly label = input.required<string>();
  readonly variant = input<BadgeVariant>('neutral');
}
