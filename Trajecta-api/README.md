# Trajecta API

![Module](https://img.shields.io/badge/module-backend-6db33f)
![Java](https://img.shields.io/badge/java-21-1f2937)
![Spring](https://img.shields.io/badge/spring%20boot-4.0.5-6db33f)

Spring Boot backend for Trajecta telemetry workflows.

Full endpoint reference: `API.md`.

## Table of Contents

- [Overview](#overview)
- [Stack](#stack)
- [Responsibilities](#responsibilities)
- [REST API](#rest-api)
- [WebSocket](#websocket)
- [Configuration](#configuration)
- [Run](#run)
- [Scripted Startup](#scripted-startup)
- [Tests and Coverage](#tests-and-coverage)
- [Observability](#observability)

## Overview

The API handles auth, task orchestration, data delivery, admin operations, and realtime events.

Core flow:
1. accept telemetry upload and create task
2. persist task and source reference
3. publish parsing request to RabbitMQ
4. receive worker result and update task
5. expose trajectory/raw downloads and notifications
6. push task/notification events via WebSocket

## Stack

- Java 21
- Spring Boot `4.0.5`
- Spring Web, Validation, Security, WebSocket
- Spring Data JPA (PostgreSQL)
- Spring AMQP (RabbitMQ)
- Spring Data Redis
- MinIO SDK
- SpringDoc OpenAPI
- JUnit 5 + JaCoCo

## Responsibilities

- JWT authentication and authorization
- task lifecycle management
- raw and trajectory streaming endpoints
- user notifications
- admin user/punishment/cache/notification tools
- internal worker-only endpoint protected by worker token

## REST API

Public base: `/api/v1/**`  
Internal base: `/api/internal/v1/**`

### Auth

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`

### Tasks

- `POST /api/v1/tasks`
- `GET /api/v1/tasks/{taskId}`
- `GET /api/v1/tasks?offset=0&limit=20`
- `POST /api/v1/tasks/{taskId}/ai-conclusion`
- `POST /api/v1/tasks/{taskId}/ai-conclusion/regenerate`
- `POST /api/v1/tasks/delete-bulk`
- `GET /api/v1/tasks/{taskId}/raw`
- `GET /api/v1/tasks/{taskId}/trajectory`

### Notifications

- `GET /api/v1/notifications`
- `PATCH /api/v1/notifications/{id}/read`
- `PATCH /api/v1/notifications/read-all`
- `DELETE /api/v1/notifications/{id}`

### User

- `GET /api/v1/users/me`
- `GET /api/v1/users/me/ban-status`
- `PUT /api/v1/users`

### Admin

Users:
- `GET /api/v1/admin/users`
- `GET /api/v1/admin/users/{id}`
- `PUT /api/v1/admin/users/{id}/role`
- `DELETE /api/v1/admin/users/{id}`

Punishments:
- `POST /api/v1/admin/punishments/ban`
- `POST /api/v1/admin/punishments/{id}/unban`
- `POST /api/v1/admin/punishments/users/{userId}/unban`

Notifications:
- `POST /api/v1/admin/notifications/broadcast`
- `POST /api/v1/admin/notifications/preview`
- `GET /api/v1/admin/notifications/history?limit=50`

Cache:
- `GET /api/v1/admin/cache/health`
- `POST /api/v1/admin/cache/users/{userId}/clear`

### Internal worker endpoint

- `GET /api/internal/v1/tasks/{taskId}/raw` (requires `X-Worker-Token`)

## WebSocket

- endpoint: `/ws`
- broker prefixes: `/topic`, `/queue`
- application prefix: `/app`
- user destination used by frontend: `/user/queue/events`
- STOMP `CONNECT` requires `Authorization: Bearer <JWT>`

## Configuration

Main config file: `src/main/resources/application.yml`.

Important environment variables:

- Database: `DB_HOST`, `DB_USER`, `DB_PASSWORD`
- RabbitMQ: `RABBIT_HOST`, `RABBIT_USER`, `RABBIT_PASS`
- Redis: `REDIS_HOST`
- MinIO: `MINIO_URL`, `MINIO_USER`, `MINIO_PASS`
- Security: `JWT_SECRET`, `INTERNAL_WORKER_TOKEN`
- AI: `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_CHAT_URL`, `OPENAI_CHAT_TIMEOUT_SECONDS`
- Rate limits:
  - `LOGIN_RATE_LIMIT_MAX_ATTEMPTS`, `LOGIN_RATE_LIMIT_WINDOW_SECONDS`
  - `TASK_CREATE_RATE_LIMIT_MAX_ATTEMPTS`, `TASK_CREATE_RATE_LIMIT_WINDOW_SECONDS`
  - `AI_CONCLUSION_RATE_LIMIT_MAX_ATTEMPTS`, `AI_CONCLUSION_RATE_LIMIT_WINDOW_SECONDS`

## Run

Local JVM run:

```powershell
.\gradlew.bat clean build
.\gradlew.bat bootRun
```

Docker compose run:

```powershell
docker compose -f compose.yaml up --build -d
```

Services in `compose.yaml`:
- `backend`
- `db`
- `redis`
- `rabbitmq`
- `minio`
- `minio-init`

## Scripted Startup

Helper script: `scripts/start-all.bat`

```bat
scripts\start-all.bat
scripts\start-all.bat skiptests
scripts\start-all.bat skipbuild
scripts\start-all.bat skipcompose
scripts\start-all.bat autologs
```

## Tests and Coverage

```powershell
.\gradlew.bat test
.\gradlew.bat jacocoTestReport
```

Coverage reports:
- `build/reports/jacoco/test/html/index.html`
- `build/reports/jacoco/test/jacocoTestReport.xml`

## Observability

- Swagger UI: `http://localhost:8080/swagger-ui`
- OpenAPI: `http://localhost:8080/api-docs`
- Health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`
- Prometheus: `http://localhost:8080/actuator/prometheus`
