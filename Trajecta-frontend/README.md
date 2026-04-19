# Trajecta Frontend

![Module](https://img.shields.io/badge/module-frontend-61dafb)
![React](https://img.shields.io/badge/react-18-1f2937)
![Vite](https://img.shields.io/badge/vite-5-646cff)

React + Vite client for Trajecta telemetry workflows.

## Table of Contents

- [Overview](#overview)
- [Stack](#stack)
- [Application Flow](#application-flow)
- [Project Map](#project-map)
- [Scripts](#scripts)
- [Environment Variables](#environment-variables)
- [UI Style Rules](#ui-style-rules)
- [Docker](#docker)

## Overview

The frontend provides:

- authentication and account screens
- upload and task management flow
- processing status and analytics workspace
- modules for replay, charts, params, diagnostics
- profile and admin screens (by role)
- realtime updates and notifications via WebSocket/STOMP messages from backend

## Stack

- React 18 + TypeScript
- Vite 5
- Tailwind CSS
- Zustand
- Cesium/Resium
- ECharts

## Application Flow

`src/App.tsx` controls top-level mode routing:

1. landing (`LandingScreen`)
2. auth (`AuthScreen`) for unauthenticated users
3. banned state (`BannedScreen`) when applicable
4. shell (`AppShell`) with sidebar + content
5. tasks/profile/admin/analytics module views

## Project Map

- `src/App.tsx` - app flow and mode routing
- `src/hooks/useFlightData.ts` - upload/select task flow, trajectory load, AI conclusion operations
- `src/lib/api.ts` - typed API client
- `src/components/layout/app-shell.tsx` - header/sidebar, realtime event handling, notifications panel
- `src/modules/replay/*` - replay and timeline tools
- `src/modules/charts/*` - telemetry charts
- `src/modules/params/*` - params table
- `src/modules/diagnostics/*` - AI diagnostics view

## Scripts

```bash
npm run dev
npm run build
npm run preview
npm run lint
```

## Environment Variables

- `VITE_APP_BASE_PATH` (used in `vite.config.ts` as Vite `base`)
- `VITE_API_BASE_URL` (used by `src/lib/api.ts`)
- `VITE_WS_PATH` (used by app shell, default `/ws`)
- `VITE_CESIUM_ION_TOKEN` (optional)

## UI Style Rules

- semantic tokens are defined in `src/styles.css`
- prefer semantic classes (`bg-background`, `text-foreground`, `border-border`)
- use `ui-field` for text fields and `textarea`
- use `ui-select` for dropdowns
- prefer shared primitives in `src/components/ui`

## Docker

Files:

- `Dockerfile`
- `docker-compose.yml`
- `nginx/default.conf.template`

Runtime behavior:

- static app is served by Nginx on container port `80`
- compose exposes host `3000:80`
- `/api/*` and `/ws` are proxied to `BACKEND_UPSTREAM` (default `backend:8080`)

Run:

```bash
docker compose up --build -d frontend
```

If `telemetry-network` does not exist:

```bash
docker network create telemetry-network
```
