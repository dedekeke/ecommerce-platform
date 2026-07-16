import { TestBed } from '@angular/core/testing';
import { StripeLoaderService } from './stripe-loader.service';

describe('StripeLoaderService', () => {
  let service: StripeLoaderService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [StripeLoaderService] });
    service = TestBed.inject(StripeLoaderService);
  });

  // The real @stripe/stripe-js SDK injects a <script> tag and talks to js.stripe.com, so the
  // "real key" path is intentionally left to be exercised via a fake in consumer specs
  // (see payment-methods.page.spec.ts) rather than here — this keeps unit tests network-free.
  it('should resolve to null without importing the SDK when no publishable key is configured', async () => {
    const result = await service.load('');
    expect(result).toBeNull();
  });

  it('should consistently resolve to null on repeated calls with no publishable key', async () => {
    const first = await service.load('');
    const second = await service.load('');
    expect(first).toBeNull();
    expect(second).toBeNull();
  });
});
