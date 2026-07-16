import { Injectable } from '@angular/core';

/** Minimal shape of the Stripe.js objects this MFE depends on — kept narrow so specs can
 * provide a fake without pulling in the real @stripe/stripe-js runtime. */
export interface StripePaymentElement {
  mount(el: HTMLElement): void;
  unmount(): void;
}

export interface StripeElementsInstance {
  create(type: 'payment'): StripePaymentElement;
}

export interface StripeConfirmSetupResult {
  error?: { message?: string };
  setupIntent?: { id: string; status: string };
}

export interface StripeInstance {
  elements(options: { clientSecret: string }): StripeElementsInstance;
  confirmSetup(options: {
    elements: StripeElementsInstance;
    redirect: 'if_required';
  }): Promise<StripeConfirmSetupResult>;
}

/**
 * Lazily loads the ~120KB Stripe.js SDK via dynamic import so it is only fetched when the
 * add-card flow is actually used (PCI SAQ-A: raw card data never touches our servers, Stripe
 * Elements collects it directly). Isolated behind this injectable so component specs can
 * provide a fake instead of hitting the network or mounting real Stripe Elements.
 */
@Injectable({ providedIn: 'root' })
export class StripeLoaderService {
  private stripePromise: Promise<StripeInstance | null> | null = null;

  load(publishableKey: string): Promise<StripeInstance | null> {
    if (!publishableKey) {
      return Promise.resolve(null);
    }
    if (!this.stripePromise) {
      this.stripePromise = import('@stripe/stripe-js').then(({ loadStripe }) =>
        loadStripe(publishableKey) as unknown as Promise<StripeInstance | null>
      );
    }
    return this.stripePromise;
  }
}
