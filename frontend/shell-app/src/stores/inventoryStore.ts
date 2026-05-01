import { create } from 'zustand'

export interface InventoryUpdate {
  productId: string
  sku: string
  availableQty: number
  previousQty: number
  ts: string
}

interface InventoryState {
  byProductId: Record<string, InventoryUpdate>
  lastError: Error | null
  setUpdate: (u: InventoryUpdate) => void
  setError: (e: Error | null) => void
  clear: () => void
}

export const useInventoryStore = create<InventoryState>((set) => ({
  byProductId: {},
  lastError: null,
  setUpdate: (u) =>
    set((state) => ({
      byProductId: { ...state.byProductId, [u.productId]: u },
    })),
  setError: (e) => set({ lastError: e }),
  clear: () => set({ byProductId: {}, lastError: null }),
}))

export const selectInventoryFor = (productId: string) =>
  (state: InventoryState): InventoryUpdate | undefined =>
    state.byProductId[productId]
