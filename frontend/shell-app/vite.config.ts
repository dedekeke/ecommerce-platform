import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig({
  resolve: {
    alias: {
      '@ecommerce/shared-ui': resolve(__dirname, '../shared-ui/src'),
    },
  },
  plugins: [
    react(),
    federation({
      name: 'shell',
      remotes: {
        productCatalog: 'http://localhost:5001/assets/remoteEntry.js',
        cart: 'http://localhost:5002/assets/remoteEntry.js',
        checkout: 'http://localhost:5003/assets/remoteEntry.js',
        userDashboard: 'http://localhost:5004/assets/remoteEntry.js',
        adminDashboard: 'http://localhost:5005/assets/remoteEntry.js',
      },
      shared: ['react', 'react-dom', 'react-router-dom', 'zustand'],
    }),
  ],
  build: {
    modulePreload: false,
    target: 'esnext',
    minify: false,
    cssCodeSplit: false,
    // §1.6 Asset hashing for long-lived CDN caching. Vite hashes by default;
    // explicit `[name].[hash]` patterns document the contract that nginx
    // (see infrastructure/cdn/nginx-static.conf) relies on for the
    // immutable Cache-Control header on /assets/*.
    assetsDir: 'assets',
    rollupOptions: {
      output: {
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]',
        // manualChunks split heavy vendor libs out of the main entry so
        // unrelated app changes don't bust the vendor cache. We do NOT
        // chunk react/react-dom/react-router-dom because they are declared
        // as `shared` in the federation plugin and must stay in the shell.
        manualChunks: {
          'mui-vendor': ['@mui/material', '@mui/icons-material', '@emotion/react', '@emotion/styled'],
          'auth-vendor': ['@auth0/auth0-react'],
          'motion-vendor': ['framer-motion'],
        },
      },
    },
  },
  server: {
    port: 5173,
    cors: true,
  },
  preview: {
    port: 5173,
  },
  test: {
    globals: true,
    environment: 'happy-dom',
    setupFiles: './src/test/setup.ts',
    css: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      exclude: [
        'node_modules/',
        'src/test/',
      ],
    },
  },
})
