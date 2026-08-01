export interface CartItemResponse {
  itemId: string;
  productId: string;
  name: string;
  price: number;
  quantity: number;
  image?: string;
}

export interface CartResponse {
  cartId: string;
  items: CartItemResponse[];
  total: number;
  itemCount: number;
}

export interface AddToCartPayload {
  productId: string;
  quantity: number;
}
