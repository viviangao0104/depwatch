import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In dev, proxy /api and /ws to the gateway to avoid CORS and hard-coded URLs
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
  build: { outDir: 'dist', sourcemap: false },
})
