import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import federation from '@originjs/vite-plugin-federation'
import { resolve } from 'path'

export default defineConfig({
  resolve: {
    alias: {
      '@ecommerce/shared-ui': resolve(__dirname, '../shared-ui/src'),
    },
  },
  plugins: [
    react(),
    federation({
      name: 'productCatalog',
      filename: 'remoteEntry.js',
      exposes: {
        './ProductCatalog': './src/ProductCatalog.tsx',
      },
      shared: ['react', 'react-dom', 'react-router-dom', 'zustand'],
    }),
  ],
  build: {
    modulePreload: false,
    target: 'esnext',
    minify: false,
    cssCodeSplit: false,
  },
  server: {
    port: 5001,
    cors: true,
  },
  preview: {
    port: 5001,
    cors: true,
  },
})
