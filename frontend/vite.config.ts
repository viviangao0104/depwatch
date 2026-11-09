import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 开发时把 /api 和 /ws 代理到网关，避免 CORS 和写死地址
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
