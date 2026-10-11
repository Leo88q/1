import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    host: '0.0.0.0',
    port: 5173,
    // Превью Arena открывается с внешнего домена — разрешаем его и туннели.
    allowedHosts: true,
    strictPort: false,
  },
  build: {
    target: 'es2022',
    outDir: 'dist',
  },
});
