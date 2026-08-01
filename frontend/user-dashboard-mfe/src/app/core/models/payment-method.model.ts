export interface SavedPaymentMethod {
  id: number;
  userId: string;
  provider: string;
  providerId: string;
  last4: string;
  brand: string;
  expMonth: number;
  expYear: number;
  isDefault: boolean;
  createdAt: string;
}

export interface SetupIntentResponse {
  setupIntentId: string;
  clientSecret: string;
}
