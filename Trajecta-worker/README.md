# Trajecta Worker (Rust)

Rust worker that consumes analysis requests from RabbitMQ, downloads source `.BIN` from backend internal API, runs INS sensor-fusion trajectory reconstruction, and publishes `AnalysisResult` back to RabbitMQ.

## What Stayed Compatible

- Rabbit topology and routing keys:
  - exchange: `telemetry.exchange`
  - request queue/routing key: `telemetry.parse`
  - result queue/routing key: `telemetry.results`
- Input request schema: `{ "taskId": <number> }`
- Output result schema:
  - `taskId`
  - `status` (`COMPLETED`/`FAILED`)
  - `trajectoryJson`
  - `metrics` (`maxAltitude`, `maxSpeed`, `flightDuration`, `distance`)
  - `errorMessage`
- Internal raw download endpoint: `/api/internal/v1/tasks/{taskId}/raw` with worker token header.

## New Processing Core

The old Python timeline pipeline is replaced with Rust INS pipeline based on DataFlash parsing + sensor fusion:

- parse FMT definitions and IMU/BARO/VIBE/GPS records from binary log
- enforce hardware constraints from challenge (`>=3` unique IMU, `>=2` unique barometers)
- fuse IMU triplet + barometers
- reconstruct continuous trajectory
- optionally compare against GT CSV if `GROUND_TRUTH_PATH` is configured

## Build and Run (Local)

```bash
cd /Users/knalis/Documents/dev/java/Trajecta/Trajecta-worker
cargo run --release
```

## Docker

```bash
cd /Users/knalis/Documents/dev/java/Trajecta/Trajecta-worker
docker compose up --build -d worker
```

## Environment Variables

- `RABBITMQ_URL`
- `REQUEST_EXCHANGE`
- `REQUEST_ROUTING_KEY`
- `REQUEST_QUEUE`
- `RESULTS_EXCHANGE`
- `RESULTS_ROUTING_KEY`
- `RESULTS_QUEUE`
- `API_BASE_URL`
- `INTERNAL_WORKER_TOKEN` (required)
- `WORKER_TOKEN_HEADER` (default `X-Worker-Token`)
- `INTERNAL_RAW_PATH_TEMPLATE` (default `/api/internal/v1/tasks/{taskId}/raw`)
- `HTTP_TIMEOUT_SECONDS` (default `30`)
- `MAX_RAW_DOWNLOAD_BYTES` (default `209715200`)
- `DOWNLOAD_CHUNK_SIZE_BYTES` (default `1048576`)
- `TIMELINE_DT` (default `0.1`)
- `GROUND_TRUTH_PATH` (optional path to CSV for offline error metrics)

## Verification

```bash
cd /Users/knalis/Documents/dev/java/Trajecta/Trajecta-worker
cargo test
cargo clippy --all-targets -- -D warnings
```
