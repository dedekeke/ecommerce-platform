import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { LoyaltyAdminService } from '../../../core/services/loyalty-admin.service';
import { LoyaltyStatus } from '../../../core/models/loyalty.model';

/**
 * Loyalty tier lookup widget (read-only). The backend only exposes a
 * per-user tier lookup (GET /api/promotions/loyalty/{userId}) — there is no
 * admin endpoint to list or edit tier thresholds, so tier configuration
 * management is a backend follow-up.
 */
@Component({
  selector: 'app-loyalty-lookup',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <section class="loyalty-lookup" aria-label="Loyalty tier lookup">
      <h2>Loyalty Tiers</h2>
      <p class="loyalty-lookup__hint" data-testid="loyalty-hint">
        Tier lookup is read-only. Tier thresholds are configured centrally; management here will follow once the
        backend exposes a tier admin endpoint.
      </p>

      <form [formGroup]="searchForm" (ngSubmit)="onSearch()" class="loyalty-lookup__form" novalidate>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>User ID</mat-label>
          <input matInput formControlName="userId" data-testid="loyalty-user-id-input" placeholder="e.g. user-1234" />
        </mat-form-field>
        <button
          mat-flat-button
          color="primary"
          type="submit"
          data-testid="loyalty-search-btn"
          [disabled]="searchForm.invalid || loading()"
        >
          {{ loading() ? 'Looking up...' : 'Look Up' }}
        </button>
      </form>

      @if (error()) {
        <p class="loyalty-lookup__error" role="alert" data-testid="loyalty-error">{{ error() }}</p>
      }

      @if (status()) {
        <div class="loyalty-lookup__result" data-testid="loyalty-result">
          <div class="loyalty-lookup__row">
            <span>Tier</span>
            <strong>{{ status()!.tier }}</strong>
          </div>
          <div class="loyalty-lookup__row">
            <span>Discount</span>
            <span>{{ status()!.discountPercent }}%</span>
          </div>
          <div class="loyalty-lookup__row">
            <span>Lifetime Spend</span>
            <span>{{ status()!.currentSpend | currency }}</span>
          </div>
          @if (status()!.nextTier) {
            <div class="loyalty-lookup__row">
              <span>Next Tier</span>
              <span>{{ status()!.nextTier }} ({{ status()!.nextTierAt | currency }} to go)</span>
            </div>
          }
        </div>
      }
    </section>
  `,
  styleUrl: './loyalty-lookup.component.scss',
})
export class LoyaltyLookupComponent {
  private readonly loyaltyService = inject(LoyaltyAdminService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly status = signal<LoyaltyStatus | null>(null);

  searchForm = this.fb.group({
    userId: ['', Validators.required],
  });

  onSearch(): void {
    if (this.searchForm.invalid) return;
    const userId = this.searchForm.getRawValue().userId!;
    this.loading.set(true);
    this.error.set(null);
    this.status.set(null);
    this.loyaltyService.getLoyaltyStatus(userId).subscribe({
      next: (status) => {
        this.loading.set(false);
        this.status.set(status);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(`No loyalty record found for user: ${userId}`);
      },
    });
  }
}
