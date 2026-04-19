# Trajecta API Reference

This file is the practical API guide for `Trajecta-api` based on controllers and security config.

## Base paths
- Public REST: `/api/v1/**`
- Internal worker REST: `/api/internal/v1/**`
- Public utility: `/api/public/**`
- WebSocket STOMP endpoint: `/ws`

## Auth and response format
- REST auth: `Authorization: Bearer <jwt>` for all protected `/api/v1/**` endpoints.
- Internal worker auth: `X-Worker-Token: <token>` for `/api/internal/**`.
- Most REST responses use `ApiResponse<T>`:
  - `success: boolean`
  - `data: T | null`
  - `error: { status, code, message } | null`
  - `timestamp: ISO-8601`
- Raw file endpoints return binary stream, not `ApiResponse`.

## Access rules (from security config)
- `permitAll`: `/api/v1/auth/**`, `/api/public/**`, `/swagger-ui/**`, `/api-docs/**`, `/actuator/health`, `/actuator/info`
- `authenticated`: `/api/v1/users/**`, `/api/v1/notifications/**`, `/api/v1/tasks/**`
- `ADMIN` or `OWNER`: `/api/v1/admin/**`
- `ROLE_INTERNAL_WORKER`: `/api/internal/**`

## Endpoint map

### Auth
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`

### Tasks
- `POST /api/v1/tasks` (multipart: `title`, `file`, optional `folderName`, optional `folderId`, optional `timelineDtSeconds`)
- `GET /api/v1/tasks/{taskId}`
- `GET /api/v1/tasks?offset=0&limit=20`
- `POST /api/v1/tasks/{taskId}/ai-conclusion`
- `POST /api/v1/tasks/{taskId}/ai-conclusion/regenerate`
- `POST /api/v1/tasks/{taskId}/reanalyze` (JSON: optional `title`, `folderName`, `folderId`, `timelineDtSeconds`)
- `POST /api/v1/tasks/compare` (JSON: `taskIds`, optional `baselineTaskId`)
- `POST /api/v1/tasks/delete-bulk` (JSON: `taskIds`)
- `POST /api/v1/tasks/move-bulk` (JSON: `taskIds`, `folderId|null`)
- `GET /api/v1/tasks/{taskId}/raw` (binary)
- `GET /api/v1/tasks/{taskId}/trajectory` (binary)

### Task folders
- `POST /api/v1/task-folders` (JSON: `name`, optional `parentId`)
- `GET /api/v1/task-folders?parentId=<id|null>` (direct children)
- `GET /api/v1/task-folders/tree` (full nested tree)
- `GET /api/v1/task-folders/{folderId}`
- `PATCH /api/v1/task-folders/{folderId}` (JSON: optional `name`, optional `parentId`)
- `DELETE /api/v1/task-folders/{folderId}?recursive=true`

### Notifications
- `GET /api/v1/notifications`
- `PATCH /api/v1/notifications/{id}/read`
- `PATCH /api/v1/notifications/read-all`
- `DELETE /api/v1/notifications/{id}`

### Current user
- `GET /api/v1/users/me`
- `GET /api/v1/users/me/ban-status`
- `PUT /api/v1/users`

### Admin users
- `GET /api/v1/admin/users?page=0&size=10`
- `GET /api/v1/admin/users/{id}`
- `PUT /api/v1/admin/users/{id}/role`
- `DELETE /api/v1/admin/users/{id}`

### Admin punishments
- `POST /api/v1/admin/punishments/ban`
- `POST /api/v1/admin/punishments/{id}/unban`
- `POST /api/v1/admin/punishments/users/{userId}/unban`

### Admin notifications
- `POST /api/v1/admin/notifications/broadcast`
- `POST /api/v1/admin/notifications/preview`
- `GET /api/v1/admin/notifications/history?limit=50`

### Admin cache/service
- `POST /api/v1/admin/cache/users/{userId}/clear`
- `GET /api/v1/admin/cache/health`
- `GET /api/v1/admin/cache/service-health`
- `GET /api/v1/admin/cache/runtime-metrics`

### Public utility
- `GET /api/public/ping` -> `{ "status": "ok" }`

### Internal worker
- `GET /api/internal/v1/tasks/{taskId}/raw` (requires `X-Worker-Token`)

## WebSocket/STOMP contract
- Endpoint: `/ws`
- Client sends STOMP `CONNECT` with native header `Authorization: Bearer <jwt>`
- Broker prefixes: `/topic`, `/queue`
- App prefix: `/app`
- User event destination used by frontend: `/user/queue/events`
- Typical event envelope: `SocketEvent` (`type`, `payload`)

## RabbitMQ worker contract
- Exchange: `telemetry.exchange`
- Parse queue/routing key: `telemetry.parse`
- Result queue/routing key: `telemetry.results`
- Analysis request payload includes `taskId` and optional `timelineDtSeconds`.

## Notes for frontend/worker integration
- Keep JSON field names camelCase for Java/Rust compatibility.
- Keep response envelope semantics for JSON endpoints.
- Keep `/raw` and `/trajectory` as stream endpoints.



