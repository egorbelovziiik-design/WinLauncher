import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// WinLaucher frontend build.
// In Tauri the app is served from the bundled dist/ folder.
export default defineConfig({
  plugins: [react()],
  clearScreen: false,
  server: {
    host: '0.0.0.0',
    port: 1420,
    strictPort: false,
    watch: { ignored: ['**/src-tauri/**'] },
  },
  envPrefix: ['VITE_', 'TAURI_'],
  build: {
    target: 'chrome105',
    minify: 'esbuild',
    sourcemap: false,
    chunkSizeWarningLimit: 1500,
  },
});
