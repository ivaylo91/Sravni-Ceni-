import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    // В режим на разработка заявките към /api отиват към Spring Boot
    proxy: { '/api': 'http://localhost:8080' },
  },
})
