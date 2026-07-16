import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { CurrencyAdminService } from '../../../core/services/currency-admin.service';
import { CurrencyRates } from '../../../core/models/currency.model';

/**
 * FX rates view + convert calculator. The backend (promotion-service's
 * CurrencyController) only exposes read/convert — there is no admin
 * endpoint to set a rate, so this widget is view/convert only.
 */
@Component({
  selector: 'app-currency-rates',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    <section class="currency-rates" aria-label="FX rates">
      <h2>FX Rates</h2>

      @if (loading()) {
        <p data-testid="rates-loading">Fetching the good stuff...</p>
      } @else if (loadError()) {
        <p class="currency-rates__error" role="alert" data-testid="rates-error">{{ loadError() }}</p>
      } @else if (rateEntries().length === 0) {
        <p data-testid="rates-empty">No FX rates configured yet.</p>
      } @else {
        <table class="currency-rates__table">
          <thead>
            <tr>
              <th scope="col">Currency</th>
              <th scope="col">Rate to {{ rates()?.base }}</th>
            </tr>
          </thead>
          <tbody>
            @for (entry of rateEntries(); track entry.code) {
              <tr data-testid="rate-row">
                <td>{{ entry.code }}</td>
                <td>{{ entry.rate }}</td>
              </tr>
            }
          </tbody>
        </table>
      }

      <h3 class="currency-rates__subheading">Convert</h3>
      <form [formGroup]="convertForm" (ngSubmit)="onConvert()" class="currency-rates__form" novalidate>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Amount</mat-label>
          <input matInput type="number" formControlName="amount" data-testid="convert-amount-input" />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>From</mat-label>
          <input matInput formControlName="from" data-testid="convert-from-input" placeholder="USD" />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>To</mat-label>
          <input matInput formControlName="to" data-testid="convert-to-input" placeholder="EUR" />
        </mat-form-field>
        <button
          mat-flat-button
          color="primary"
          type="submit"
          data-testid="convert-btn"
          [disabled]="convertForm.invalid || converting()"
        >
          {{ converting() ? 'Converting...' : 'Convert' }}
        </button>
      </form>

      @if (convertError()) {
        <p class="currency-rates__error" role="alert" data-testid="convert-error">{{ convertError() }}</p>
      }
      @if (convertResult() !== null) {
        <p data-testid="convert-result">{{ convertResult() }}</p>
      }
    </section>
  `,
  styleUrl: './currency-rates.component.scss',
})
export class CurrencyRatesComponent implements OnInit {
  private readonly currencyService = inject(CurrencyAdminService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly rates = signal<CurrencyRates | null>(null);
  readonly converting = signal(false);
  readonly convertError = signal<string | null>(null);
  readonly convertResult = signal<number | null>(null);

  convertForm = this.fb.group({
    amount: [0 as number | null, [Validators.required, Validators.min(0)]],
    from: ['', Validators.required],
    to: ['', Validators.required],
  });

  ngOnInit(): void {
    this.currencyService.getRates().subscribe({
      next: (rates) => {
        this.rates.set(rates);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set('Failed to load FX rates.');
        this.loading.set(false);
      },
    });
  }

  rateEntries(): { code: string; rate: number }[] {
    const rates = this.rates()?.rates;
    if (!rates) return [];
    return Object.entries(rates).map(([code, rate]) => ({ code, rate }));
  }

  onConvert(): void {
    if (this.convertForm.invalid) return;
    const { amount, from, to } = this.convertForm.getRawValue();
    this.converting.set(true);
    this.convertError.set(null);
    this.convertResult.set(null);
    this.currencyService.convert({ amount: amount!, from: from!, to: to! }).subscribe({
      next: (result) => {
        this.converting.set(false);
        this.convertResult.set(result.amount);
      },
      error: () => {
        this.converting.set(false);
        this.convertError.set('Conversion failed. Check the currency codes and try again.');
      },
    });
  }
}
