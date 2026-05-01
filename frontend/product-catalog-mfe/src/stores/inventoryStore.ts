import { create } from 'zustand'

// Mirrors shell-app's inventoryStore shape. Federated zustand share-scope
// resolves both to the same singleton at runtime; this mirror only kicks in
// when the MFE runs standalone in dev.
export interface InventoryUpdate {
  productId: string
  sku: string
  availableQty: number
  previousQty: number
  ts: string
}

interface InventoryState {
  byProductId: Record<string, InventoryUpdate>
  setUpdate: (u: InventoryUpdate) => void
  clear: () => void
}

export const useInventoryStore = create<InventoryState>((set) => ({
  byProductId: {},
  setUpdate: (u) =>
    set((state) => ({
      byProductId: { ...state.byProductId, [u.productId]: u },
    })),
  clear: () => set({ byProductId: {} }),
}))

export const selectInventoryFor = (productId: string) =>
  (state: InventoryState): InventoryUpdate | undefined =>
    state.byProductId[productId]
