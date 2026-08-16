import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

const allowedHosts = (process.env.VITE_DEV_ALLOWED_HOSTS || '')
  .split(',')
  .map((host) => host.trim())
  .filter(Boolean)

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    ...(allowedHosts.length > 0 ? { allowedHosts } : {}),
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_API_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
