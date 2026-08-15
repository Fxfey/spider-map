import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The plugin serves the UI and the API from one origin, so the browser never
// deals with CORS (SDLC §1). `npm run dev` breaks that — Vite serves on 5173
// while the plugin listens elsewhere — so proxy /api through to keep the dev
// server behaving like production.
//
// Override the target when the plugin isn't on the default port:
//   SPIDER_MAP_PORT=8081 npm run dev
const apiPort = process.env.SPIDER_MAP_PORT ?? '8080'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: `http://localhost:${apiPort}`,
        changeOrigin: true,
      },
    },
  },
  build: {
    // Gradle copies this into the plugin jar's resources; see build.gradle.kts.
    outDir: 'dist',
    emptyOutDir: true,
  },
})
