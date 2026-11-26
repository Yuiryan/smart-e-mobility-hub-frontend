import { defineConfig, loadEnv } from 'vite';
export default defineConfig(({ mode }) => {
  const demo = mode === 'iot-demo';
  const env = loadEnv(mode, process.cwd(), '');
  return {
    define:
      mode === 'api' || demo
        ? { 'import.meta.env.VITE_DATA_SOURCE': JSON.stringify('api') }
        : {},
    cacheDir: demo ? 'node_modules/.vite-iot-demo' : 'node_modules/.vite',
    server: {
      port: demo ? 5174 : 5173,
      strictPort: true,
      proxy: {
        '/api': {
          target: demo
            ? env.IOT_DEMO_API_PROXY_TARGET || 'http://localhost:8081'
            : env.API_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  };
});
