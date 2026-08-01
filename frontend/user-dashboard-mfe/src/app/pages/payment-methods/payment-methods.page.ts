import {
  AfterViewChecked,
  Component,
  ElementRef,
  OnInit,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { PaymentMethodService } from '../../core/services/payment-method.service';
import {
  StripeElementsInstance,
  StripeInstance,
  StripeLoaderService,
} from '../../core/services/stripe-loader.service';
import { STRIPE_PUBLISHABLE_KEY } from '../../core/config/stripe.config';
import { SavedPaymentMethod } from '../../core/models/payment-method.model';
import { PaymentMethodCardComponent } from '../../shared/components/payment-method-card/payment-method-card.component';

const DEMO_USER_ID = 'me';

type AddCardState = 'idle' | 'initializing' | 'ready' | 'submitting' | 'blocked';

const GENERIC_ADD_CARD_ERROR = "That didn't work. Let's try again?";

@Component({
  selector: 'app-payment-methods-page',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    PaymentMethodCardComponent,
  ],
  template: `
    <div class="payment-methods-page container">
      <header class="payment-methods-page__header">
        <h1 class="payment-methods-page__title">Payment Methods</h1>
        <button
          mat-flat-button
          color="primary"
          data-testid="add-card-btn"
          (click)="onAddCardClick()"
          [disabled]="addCardState() !== 'idle'"
          aria-label="Add a new payment method"
        >
          <mat-icon aria-hidden="true">add</mat-icon>
          Add Card
        </button>
      </header>

      @if (addCardState() !== 'idle') {
        <section
          class="payment-methods-page__add-panel"
          role="region"
          aria-label="Add a new card"
          data-testid="add-card-panel"
        >
          <h2 class="payment-methods-page__add-title">Add a new card</h2>

          @if (addCardError()) {
            <p class="payment-methods-page__add-error" role="alert" data-testid="add-card-error">
              {{ addCardError() }}
            </p>
          }

          @if (addCardState() === 'initializing') {
            <div
              class="payment-methods-page__add-loading"
              role="status"
              aria-label="Preparing secure payment form"
            >
              <mat-spinner diameter="32"></mat-spinner>
              <span>Preparing something beautiful…</span>
            </div>
          }

          @if (addCardState() === 'ready' || addCardState() === 'submitting') {
            <div
              #cardElementContainer
              class="payment-methods-page__card-element"
              data-testid="stripe-card-element"
            ></div>

            <div class="payment-methods-page__add-actions">
              <button
                mat-flat-button
                color="primary"
                data-testid="confirm-add-card-btn"
                (click)="onConfirmAddCard()"
                [disabled]="addCardState() === 'submitting'"
                aria-label="Save this card"
              >
                {{ addCardState() === 'submitting' ? 'Saving…' : 'Save card' }}
              </button>
              <button
                mat-button
                data-testid="cancel-add-card-btn"
                (click)="onCancelAddCard()"
                [disabled]="addCardState() === 'submitting'"
                aria-label="Cancel adding a card"
              >
                Cancel
              </button>
            </div>
          }

          @if (addCardState() === 'blocked') {
            <div class="payment-methods-page__add-actions">
              <button
                mat-button
                data-testid="close-add-card-btn"
                (click)="onCancelAddCard()"
                aria-label="Close add card form"
              >
                Close
              </button>
            </div>
          }
        </section>
      }

      @if (loading()) {
        <div
          class="payment-methods-skeleton"
          role="status"
          aria-label="Loading payment methods"
          data-testid="loading-skeleton"
        >
          @for (row of skeletonRows; track row) {
            <div class="payment-methods-skeleton__card"></div>
          }
        </div>
      } @else if (error()) {
        <div class="payment-methods-page__error-state" role="alert" data-testid="error-state">
          <p>Oops! We couldn't load your payment methods.</p>
          <button
            mat-flat-button
            color="primary"
            data-testid="retry-btn"
            (click)="loadMethods()"
            aria-label="Retry loading payment methods"
          >
            Try again
          </button>
        </div>
      } @else if (methods().length === 0) {
        <div class="payment-methods-page__empty" data-testid="empty-state">
          <mat-icon class="payment-methods-page__empty-icon" aria-hidden="true">credit_card_off</mat-icon>
          <p>No saved cards yet. Add one to check out faster next time!</p>
        </div>
      } @else {
        <ul class="payment-methods-list" role="list" aria-label="Saved payment methods">
          @for (method of methods(); track method.id) {
            <li>
              <app-payment-method-card
                [method]="method"
                [deleting]="deletingId() === method.id"
                (delete)="onDelete($event)"
                (setDefault)="onSetDefault($event)"
              />
            </li>
          }
        </ul>
      }
    </div>
  `,
  styleUrl: './payment-methods.page.scss',
})
export class PaymentMethodsPage implements OnInit, AfterViewChecked {
  private readonly paymentMethodService = inject(PaymentMethodService);
  private readonly stripeLoader = inject(StripeLoaderService);

  @ViewChild('cardElementContainer') cardElementContainer?: ElementRef<HTMLDivElement>;

  readonly skeletonRows = [0, 1];

  readonly methods = signal<SavedPaymentMethod[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly deletingId = signal<number | null>(null);

  readonly addCardState = signal<AddCardState>('idle');
  readonly addCardError = signal<string | null>(null);

  private stripe: StripeInstance | null = null;
  private elements: StripeElementsInstance | null = null;
  private mounted = false;

  ngOnInit(): void {
    this.loadMethods();
  }

  ngAfterViewChecked(): void {
    if (
      this.addCardState() === 'ready' &&
      this.elements &&
      !this.mounted &&
      this.cardElementContainer
    ) {
      this.mounted = true;
      this.elements.create('payment').mount(this.cardElementContainer.nativeElement);
    }
  }

  loadMethods(): void {
    this.loading.set(true);
    this.error.set(false);
    this.paymentMethodService.list(DEMO_USER_ID).subscribe({
      next: (list) => {
        this.methods.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }

  onAddCardClick(): void {
    this.addCardState.set('initializing');
    this.addCardError.set(null);
    this.paymentMethodService.createSetupIntent().subscribe({
      next: (response) => this.initStripeElements(response.clientSecret),
      error: () => {
        this.addCardState.set('blocked');
        this.addCardError.set("We couldn't start the add-card process. Please try again.");
      },
    });
  }

  onConfirmAddCard(): void {
    if (!this.stripe || !this.elements) {
      return;
    }
    this.addCardState.set('submitting');
    this.addCardError.set(null);

    this.stripe
      .confirmSetup({ elements: this.elements, redirect: 'if_required' })
      .then((result) => {
        if (result.error) {
          this.addCardError.set(result.error.message ?? GENERIC_ADD_CARD_ERROR);
          this.addCardState.set('ready');
          return;
        }
        const setupIntentId = result.setupIntent?.id;
        if (!setupIntentId) {
          this.addCardError.set(GENERIC_ADD_CARD_ERROR);
          this.addCardState.set('ready');
          return;
        }
        this.paymentMethodService.confirmSetupIntent(setupIntentId).subscribe({
          next: () => {
            this.resetAddCard();
            this.loadMethods();
          },
          error: () => {
            this.addCardError.set(
              'Your card was verified but we could not save it. Please try again.'
            );
            this.addCardState.set('ready');
          },
        });
      })
      .catch(() => {
        this.addCardError.set(GENERIC_ADD_CARD_ERROR);
        this.addCardState.set('ready');
      });
  }

  onCancelAddCard(): void {
    this.resetAddCard();
  }

  onDelete(id: number): void {
    this.deletingId.set(id);
    this.paymentMethodService.delete(id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.loadMethods();
      },
      error: () => {
        this.deletingId.set(null);
      },
    });
  }

  onSetDefault(id: number): void {
    this.paymentMethodService.setDefault(id).subscribe({
      next: () => this.loadMethods(),
      error: () => undefined,
    });
  }

  private initStripeElements(clientSecret: string): void {
    this.stripeLoader
      .load(STRIPE_PUBLISHABLE_KEY)
      .then((stripe) => {
        if (!stripe) {
          this.addCardState.set('blocked');
          this.addCardError.set('Payment is not configured. Please contact support.');
          return;
        }
        this.stripe = stripe;
        this.elements = stripe.elements({ clientSecret });
        this.mounted = false;
        this.addCardState.set('ready');
      })
      .catch(() => {
        this.addCardState.set('blocked');
        this.addCardError.set("We couldn't load the secure payment form. Please try again.");
      });
  }

  private resetAddCard(): void {
    this.addCardState.set('idle');
    this.addCardError.set(null);
    this.stripe = null;
    this.elements = null;
    this.mounted = false;
  }
}
