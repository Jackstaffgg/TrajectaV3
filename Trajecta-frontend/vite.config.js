import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import cesium from "vite-plugin-cesium";
import path from "node:path";
export default defineConfig(function (_a) {
    var mode = _a.mode;
    var env = loadEnv(mode, process.cwd(), "");
    var basePath = (env.VITE_APP_BASE_PATH || "/").trim();
    return {
        base: basePath,
        plugins: [react(), cesium()],
        resolve: {
            alias: {
                "@": path.resolve(__dirname, "./src")
            }
        }
    };
});
