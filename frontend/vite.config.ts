import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    rollupOptions: {
      output: {
        // Библиотеките се сменят рядко – отделни файлове се кешират между версиите
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          supabase: ['@supabase/supabase-js'],
          icons: ['@fortawesome/fontawesome-svg-core', '@fortawesome/free-solid-svg-icons', '@fortawesome/react-fontawesome'],
        },
      },
    },
  },
  server: {
    port: 5173,
    // В режим на разработка заявките към /api отиват към Spring Boot
    proxy: { '/api': 'http://localhost:8080' },
  },
})
