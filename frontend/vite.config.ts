import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

function parsePort(value: string | undefined, fallback: number) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 && parsed <= 65_535 ? parsed : fallback
}

function parseAllowedHosts(value: string | undefined) {
  return String(value ?? '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '..', '')
  const backendTarget = env.VITE_BACKEND_PROXY_TARGET || 'http://localhost:8080'
  const allowedHosts = parseAllowedHosts(env.VITE_ALLOWED_HOSTS)

  return {
    envDir: '..',
    plugins: [vue()],
    server: {
      host: '0.0.0.0',
      port: parsePort(env.VITE_DEV_SERVER_PORT, 5174),
      ...(allowedHosts.length > 0 ? { allowedHosts } : {}),
      proxy: {
        '/api': {
          target: backendTarget,
          changeOrigin: true,
        },
        '/actuator': {
          target: backendTarget,
          changeOrigin: true,
        },
      },
    },
  }
})
