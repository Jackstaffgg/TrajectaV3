import React from "react";
import ReactDOM from "react-dom/client";
import * as Cesium from "cesium";
import App from "@/App";
import "cesium/Build/Cesium/Widgets/widgets.css";
import "@/styles.css";

const globalScope = globalThis as typeof globalThis & {
  Cesium?: typeof Cesium;
  CESIUM_BASE_URL?: string;
};

const baseUrl = (import.meta.env.BASE_URL || "/").replace(/\/?$/, "/");
globalScope.Cesium = Cesium;
globalScope.CESIUM_BASE_URL = `${baseUrl}cesium/`;

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
