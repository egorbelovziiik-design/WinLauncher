import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  root: fileURLToPath(new URL('.', import.meta.url)),
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 4173,
    strictPort: true,
    allowedHosts: ['.e2b.app', 'localhost'],
    fs: { allow: [fileURLToPath(new URL('..', import.meta.url))] },
  },
  preview: { host: '0.0.0.0', port: 4173, strictPort: true, allowedHosts: ['.e2b.app', 'localhost'] },
  build: { outDir: 'dist', emptyOutDir: true, target: 'es2022', sourcemap: false },
});
