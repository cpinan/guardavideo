/// <reference types="vitest" />
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
  // En desarrollo el proxy evita CORS: el navegador habla con el mismo origen.
  server: {
    proxy: { "/api": "http://localhost:8000", "/health": "http://localhost:8000" },
    // Vite rechaza Hosts desconocidos; permitimos los nombres MagicDNS de Tailscale (*.ts.net).
    allowedHosts: [".ts.net"],
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: false,
  },
});
