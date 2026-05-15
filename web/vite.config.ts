import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const allowLan = env.EXPLORER_ALLOW_LAN === "1";
  const lanHost = env.EXPLORER_LAN_HOST || "";

  return {
    plugins: [react()],
    server: {
      host: allowLan ? "0.0.0.0" : "127.0.0.1",
      port: 5173,
      strictPort: true,
      // /api/* → FastAPI 백엔드 (기본 127.0.0.1:8000)
      proxy: {
        "/api": {
          target: env.EXPLORER_API_TARGET || "http://127.0.0.1:8000",
          changeOrigin: false,
        },
      },
    },
    preview: {
      host: allowLan ? "0.0.0.0" : "127.0.0.1",
      port: 5173,
    },
    build: {
      outDir: "dist",
      sourcemap: true,
    },
    define: {
      // 빌드 시점에 정적으로 주입 (사외 데이터 유출 위험성 0)
      __ALLOW_LAN__: JSON.stringify(allowLan),
      __LAN_HOST__: JSON.stringify(lanHost),
    },
  };
});
