import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

// Puerto 5186 y proxy /api → api-gateway (8086). Con `npm run dev:mock` (VITE_MOCK=true) MSW responde en el navegador.
export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
    port: 5186,
    strictPort: true,
    proxy: { '/api': { target: 'http://localhost:8086', changeOrigin: true } },
  },
  preview: { port: 5186 },
  build: {
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        manualChunks: { react: ['react', 'react-dom', 'react-router-dom'], formularios: ['react-hook-form', 'zod', '@hookform/resolvers'] },
      },
    },
  },
});
