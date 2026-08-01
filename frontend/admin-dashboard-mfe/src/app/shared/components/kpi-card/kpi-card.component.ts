import { Component, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-kpi-card',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule],
  template: `
    <mat-card class="kpi-card" [attr.aria-label]="title() + ' KPI card'">
      <mat-card-content>
        <div class="kpi-card__header">
          <span class="kpi-card__title">{{ title() }}</span>
          @if (icon()) {
            <mat-icon class="kpi-card__icon" [attr.aria-hidden]="true">{{ icon() }}</mat-icon>
          }
        </div>
        <div class="kpi-card__value" data-testid="kpi-value">{{ formattedValue() }}</div>
        @if (delta() !== null && delta() !== undefined) {
          <div
            class="kpi-card__delta"
            [class.kpi-card__delta--up]="(delta() ?? 0) >= 0"
            [class.kpi-card__delta--down]="(delta() ?? 0) < 0"
            data-testid="kpi-delta"
          >
            <mat-icon class="kpi-card__delta-icon" [attr.aria-hidden]="true">
              {{ (delta() ?? 0) >= 0 ? 'trending_up' : 'trending_down' }}
            </mat-icon>
            <span>{{ deltaLabel() }}</span>
          </div>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: [`
    .kpi-card {
      border-radius: var(--radius-lg, 16px);

      &__header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 8px;
      }

      &__title {
        font-size: 0.8125rem;
        font-weight: 500;
        color: var(--color-gray-500, #71717a);
        text-transform: uppercase;
        letter-spacing: 0.05em;
      }

      &__icon {
        color: var(--color-accent, #6366f1);
        font-size: 20px;
        width: 20px;
        height: 20px;
      }

      &__value {
        font-size: 2rem;
        font-weight: 700;
        letter-spacing: -0.02em;
        color: var(--color-primary, #0a0a0a);
        line-height: 1;
        margin-bottom: 8px;
      }

      &__delta {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        font-size: 0.8125rem;
        font-weight: 600;

        &--up   { color: var(--color-success, #10b981); }
        &--down { color: var(--color-error, #ef4444); }

        &-icon {
          font-size: 16px;
          width: 16px;
          height: 16px;
        }
      }
    }
  `],
})
export class KpiCardComponent {
  readonly title = input.required<string>();
  readonly value = input.required<number | string>();
  readonly delta = input<number | null>(null);
  readonly icon = input<string>('');
  readonly prefix = input<string>('');
  readonly suffix = input<string>('');

  readonly formattedValue = computed(() => `${this.prefix()}${this.value()}${this.suffix()}`);
  readonly deltaLabel = computed(() => {
    const d = this.delta();
    if (d === null || d === undefined) return '';
    const sign = d >= 0 ? '+' : '';
    return `${sign}${d}% vs yesterday`;
  });
}
