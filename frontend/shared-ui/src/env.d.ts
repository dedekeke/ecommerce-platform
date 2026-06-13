/// <reference types="vite/client" />

/** Minimal process declaration for vi.stubEnv compatibility — no @types/node needed. */
declare const process: {
  env: Record<string, string | undefined>
}
