export interface WishlistItem {
  id: string;
  productId: string;
  productName: string;
  imageUrl?: string;
  price: number;
  currency: string;
  addedAt: string;
  inStock: boolean;
}
