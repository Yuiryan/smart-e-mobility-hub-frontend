import { defineConfig, loadEnv } from 'vite';
export default defineConfig(({ mode }) => ({
  define:
    mode === 'api'
      ? { 'import.meta.env.VITE_DATA_SOURCE': JSON.stringify('api') }
      : {},
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target:
          loadEnv(mode, process.cwd(), '').API_PROXY_TARGET ||
          'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
}));
