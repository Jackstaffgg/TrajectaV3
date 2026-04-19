# Trajecta

![Repo](https://img.shields.io/badge/repo-monorepo-2563eb)
![Backend](https://img.shields.io/badge/backend-Spring%20Boot%204.0.5-6db33f)
![Frontend](https://img.shields.io/badge/frontend-React%2018-61dafb)
![Worker](https://img.shields.io/badge/worker-Python%203.12-3776ab)
![Deploy](https://img.shields.io/badge/deploy-Docker%20Compose-2496ed)

Trajecta is an end-to-end telemetry processing and visualization platform for ArduPilot flight logs.

It includes:
- a Java backend API for auth, task orchestration, notifications, and admin operations
- a Python worker for parsing `.bin` logs and producing normalized trajectories
- a React frontend for upload, replay, analytics, diagnostics, and administration

## Table of Contents

- [Platform Overview](#platform-overview)
- [Mathematical Model](#mathematical-model)
- [Architecture](#architecture)
- [Repository Layout](#repository-layout)
- [Quick Start](#quick-start)
- [Run Modes](#run-modes)
- [Configuration](#configuration)
- [Services and Ports](#services-and-ports)
- [Quality Checks](#quality-checks)
- [Deployment](#deployment)
- [Operational Notes](#operational-notes)
- [Troubleshooting](#troubleshooting)
- [Documentation Map](#documentation-map)

## Platform Overview

Core workflow:
1. User uploads a telemetry `.bin` file from the web UI.
2. Backend stores source file and creates an analysis task.
3. Backend publishes a parse request to RabbitMQ.
4. Worker downloads raw telemetry from internal API, parses it, and publishes a result.
5. Backend stores trajectory output, updates task state, and emits WebSocket events.
6. Frontend receives realtime updates and renders replay/charts/diagnostics.

## Mathematical Model

This section describes the trajectory reconstruction model implemented by the active Rust worker (`Trajecta-worker/src/main.rs`).

### 1) Coordinate frame and discretization

- The trajectory is reconstructed in local ENU coordinates (East, North, Up), meters.
- IMU/BARO/VIBE samples are binned to a fixed 20 ms grid.
- Effective integration step is:
  - `dt = clamp((t_k - t_{k-1}), 0.005, 0.1)` seconds.

### 2) Multi-IMU robust fusion (per time step)

For each IMU instance, worker computes a robust mean of accel/gyro channels, then fuses IMUs using residual-based weights:

- Robust center from median/MAD filtering.
- Residual for candidate `i`: `r_i = ||u_i - u_center||`.
- Scale: `s = robust_average(residuals)`.
- Weight: `w_i = 1 / (1 + (r_i / s)^2)`.
- Fused vector: `u_fused = sum(w_i * u_i) / sum(w_i)`.

This suppresses outlier IMU instances without hard switching.

### 3) Attitude model (roll, pitch, yaw)

Attitude is estimated by gyro integration with accelerometer correction:

- Gyro integration:
  - `roll_k  = roll_{k-1}  + gx * dt`
  - `pitch_k = pitch_{k-1} + gy * dt`
  - `yaw_k   = wrap(yaw_{k-1} + gz * dt)`

- Accelerometer gravity correction (complementary term), when `|a|` is physically plausible:
  - `roll_acc  = atan2(a_y, a_z)`
  - `pitch_acc = atan2(-a_x, sqrt(a_y^2 + a_z^2))`
  - `roll  <- (1 - beta) * roll  + beta * roll_acc`
  - `pitch <- (1 - beta) * pitch + beta * pitch_acc`

`beta` is adaptive and reduced under high vibration.

### 4) Translational dynamics and 1D Kalman filter per axis

For each axis `a in {x, y, z}`, state is:

- `x_a = [p_a, v_a, b_a]^T`
  - `p_a`: position
  - `v_a`: velocity
  - `b_a`: acceleration bias

Process model:

- `a_corr = a_measured - b_{k-1}`
- `p_k = p_{k-1} + v_{k-1} * dt + 0.5 * a_corr * dt^2`
- `v_k = v_{k-1} + a_corr * dt`
- `b_k = b_{k-1} + w_b` (bias random walk)

Linearized transition matrix:

```text
F = [ 1   dt  -0.5*dt^2
      0   1   -dt
      0   0    1 ]
```

Process noise (from code):

```text
q_acc  = sigma_acc^2
q_bias = sigma_bias_rw^2 * dt

Q = [ 0.25*dt^4*q_acc   0.5*dt^3*q_acc   0
      0.5*dt^3*q_acc    dt^2*q_acc       0
      0                 0                q_bias ]
```

Covariance update uses Joseph form in measurement step for numeric stability.

### 5) Measurements and constraints

- Barometer update on Z position:
  - measurement model `z_baro = p_z + noise`
  - baro variance is adaptive to vibration and sensor spread.

- Zero-velocity updates (ZUPT) in quasi-static windows:
  - if platform is near-static (`|a|-g`, gyro norm, vibration, and streak checks), enforce:
    - `v_x = 0`, `v_y = 0`, `v_z = 0` with configured variances.

- Gyro bias adaptation:
  - in quasi-static windows, gyro bias is slowly re-estimated by exponential blending.

### 6) Adaptive-noise policy

Process and measurement noise are not constants; they scale with:

- vibration level,
- IMU inter-sensor spread,
- barometer spread,
- detected lag gaps.

This is critical for stable behavior across calm and high-vibration segments.

### 7) Flight-window selection and sanitization

- If FSTG stage markers exist, worker trims data to active mission window.
- Sensor data is sanitized by physical bounds (accel/gyro/baro/gps limits).
- GPS is converted to ENU and smoothed for reference track preparation.

### 8) Important constraint: GPS usage

GPS is **not used as a feedback input** to the INS state propagation in this model.

- GPS/GT tracks are used only for:
  - post-hoc alignment,
  - quality metrics,
  - visualization payload fields.

### 9) Post-processing and comparison metrics

After reconstruction:

- Trajectory is sanitized from spikes.
- Optional rigid alignment to reference track (yaw + translation) is applied for comparison/visualization.

Reported metrics:

- Endpoint error: robust tail estimate against reference.
- RMS crosstrack error:
  - `RMS = sqrt(mean(e_i^2))`
- ATE (mean absolute trajectory error):
  - `ATE = mean(e_i)`
- P95 crosstrack error: 95th percentile of `e_i`.
- Horizontal and 3D path lengths with physically implausible jumps rejected.

## Architecture

```text
Browser (React + Vite)
    |
    | HTTP / WebSocket
    v
Frontend (Nginx, Trajecta-frontend)
    |                             \
    | /api/* and /ws proxied       \ static assets
    v                               \
Backend (Spring Boot, Trajecta-api)
    |
    | RabbitMQ request/result messages
    v
Worker (Python, Trajecta-worker)

Data services used by backend:
- PostgreSQL
- Redis
- MinIO
```

For VPS deployment, `docker-compose.vps.yml` also adds Caddy (`edge`) in front of frontend/backend.

## Repository Layout

```text
Trajecta/
  README.md
  DEPLOY_VPS.md
  deploy-vps.sh
  docker-compose.vps.yml
  start-all-stacks.bat
  start-all-stacks.sh
  infra/caddy/Caddyfile
  Trajecta-api/
  Trajecta-frontend/
  Trajecta-worker/
```

## Quick Start

### Recommended (Windows)

From repository root:

```bat
start-all-stacks.bat
```

### Recommended (macOS/Linux)

From repository root:

```bash
chmod +x ./start-all-stacks.sh
./start-all-stacks.sh
```

Useful commands:

```bash
./start-all-stacks.sh init
./start-all-stacks.sh up skiptests
./start-all-stacks.sh status
./start-all-stacks.sh logs
./start-all-stacks.sh down
```

Useful commands:

```bat
start-all-stacks.bat status
start-all-stacks.bat logs
start-all-stacks.bat validate
start-all-stacks.bat up build
start-all-stacks.bat up tests
start-all-stacks.bat up workers 2
set WORKER_REPLICAS=2 && start-all-stacks.bat up
start-all-stacks.bat down
```

Host diagnostics (from repo root):

```powershell
.\check-system.ps1
.\check-system.ps1 -ApiUrl "http://localhost:8080" -FrontendUrl "http://localhost:3000" -UsersCount 50
.\check-system.ps1 -AdminToken "<jwt_admin_token>"
.\check-system.ps1 -UsersCount 120 -ProbePath "/api/public/ping"
```

```bash
./check-system.sh
ADMIN_TOKEN="<jwt_admin_token>" ./check-system.sh "http://localhost:8080" 50 "http://localhost:3000"
```

Typical local URLs:
- Frontend: `http://localhost:3000`
- API Swagger UI: `http://localhost:8080/swagger-ui`
- RabbitMQ UI: `http://localhost:15672`
- MinIO Console: `http://localhost:9001`

Local bootstrap users (created automatically on first local start):
- Owner: `Owner` / `Owner123`
- Admin: `Admin` / `Admin123`

Default local BIN upload/processing limit: `200MB`.

## Run Modes

### Monorepo script mode

`start-all-stacks.bat` performs:
1. Optional API Gradle build (`build` or `tests` flags)
2. API compose startup (`Trajecta-api/compose.yaml`, `backend`)
3. Worker compose startup (`Trajecta-worker/docker-compose.yml`, `worker`)
4. Frontend compose startup (`Trajecta-frontend/docker-compose.yml`, `frontend`)

By default, script starts all stacks via Docker Compose without Gradle build.
Worker service can be scaled with `workers N` argument or `WORKER_REPLICAS` environment variable.

### Manual module mode

Backend:

```powershell
Set-Location .\Trajecta-api
.\gradlew.bat clean build
.\gradlew.bat bootRun
```

Worker:

```powershell
Set-Location .\Trajecta-worker
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python main.py --worker
```

Frontend:

```powershell
Set-Location .\Trajecta-frontend
npm install
npm run dev
```

## Configuration

### Local compose defaults

Each module compose file contains local defaults (for development only).

### VPS environment

VPS deploy uses:
- `docker-compose.vps.yml`
- `.env.vps` (required)
- `deploy-vps.sh`

Important: if you use `docker compose` directly, pass `--env-file .env.vps`.
Without it, `${...}` values from `docker-compose.vps.yml` resolve as empty and services may fail to start.

Create `.env.vps` from template:

```bash
cp .env.vps.example .env.vps
```

High-level variable groups:
- TLS and domain: `DOMAIN`, `ACME_EMAIL`
- Backend and security: `DB_*`, `RABBIT_*`, `MINIO_*`, `JWT_SECRET`, `INTERNAL_WORKER_TOKEN`
- Frontend routing: `VITE_APP_BASE_PATH`, `VITE_API_BASE_URL`, `VITE_WS_PATH`
- Worker runtime: `HTTP_TIMEOUT_SECONDS`
- Worker scaling and limits: `WORKER_REPLICAS`, `WORKER_CPU_LIMIT`, `WORKER_MEM_LIMIT`

For a single 3 CPU / 4 GB VPS, a practical baseline is:
- `WORKER_REPLICAS=2`
- `WORKER_CPU_LIMIT=0.7`
- `WORKER_MEM_LIMIT=576m`
- `BACKEND_MEM_LIMIT=768m`

## Services and Ports

### Local API compose (`Trajecta-api/compose.yaml`)

- `backend`: `8080:8080`
- `db`: `5432:5432`
- `redis`: `6379:6379`
- `rabbitmq`: `5672:5672`, `15672:15672`
- `minio`: `9000:9000`, `9001:9001`

### Local frontend compose (`Trajecta-frontend/docker-compose.yml`)

- `frontend`: `3000:80`

### Local worker compose (`Trajecta-worker/docker-compose.yml`)

- `worker` (no published host port)

### VPS compose (`docker-compose.vps.yml`)

- Public entry: Caddy `80:80`, `443:443`
- RabbitMQ management and MinIO ports bound to localhost only

## Quality Checks

Backend:

```powershell
Set-Location .\Trajecta-api
.\gradlew.bat test
.\gradlew.bat jacocoTestReport
```

Worker:

```powershell
Set-Location .\Trajecta-worker
python -m unittest discover -s tests -v
```

Frontend:

```powershell
Set-Location .\Trajecta-frontend
npm run lint
npm run build
```

## Deployment

### VPS helper script

```bash
./deploy-vps.sh validate
./deploy-vps.sh up
./deploy-vps.sh status
./deploy-vps.sh logs
./deploy-vps.sh restart
./deploy-vps.sh down
./deploy-vps.sh wipe --yes
```

VPS volume reset helper:

```bash
chmod +x reset-vps-volumes.sh
```

```bash
./reset-vps-volumes.sh all --down --yes
./reset-vps-volumes.sh db redis --down --yes
./reset-vps-volumes.sh minio --yes
./reset-vps-volumes.sh edge --dry-run
```

Direct compose equivalents:

```bash
docker compose --env-file .env.vps -f docker-compose.vps.yml config >/dev/null
docker compose --env-file .env.vps -f docker-compose.vps.yml up -d --build --remove-orphans
docker compose --env-file .env.vps -f docker-compose.vps.yml ps
```

For full production notes and setup sequence, see `DEPLOY_VPS.md`.

## Operational Notes

- Keep secrets out of git.
- Rotate credentials if leaked.
- Keep production `.env.vps` only on the server.
- Internal worker endpoint authorization depends on `INTERNAL_WORKER_TOKEN` and `X-Worker-Token` header.
- WebSocket auth requires `Authorization: Bearer <JWT>` in STOMP `CONNECT` headers.

## Troubleshooting

- Compose startup issues:
  - verify Docker Desktop/daemon is running
  - run `start-all-stacks.bat validate` or `./deploy-vps.sh validate`
- Frontend loads but API calls fail:
  - verify backend service is healthy
  - verify `VITE_API_BASE_URL` and nginx upstream settings
- Worker fails to process tasks:
  - verify RabbitMQ connectivity and queue names
  - verify `INTERNAL_WORKER_TOKEN` is set consistently

## Documentation Map

- `Trajecta-api/README.md` - backend endpoints, config, run and tests
- `Trajecta-frontend/README.md` - frontend architecture, env, docker and scripts
- `Trajecta-worker/README.md` - worker CLI modes, env, docker and tests
- `DEPLOY_VPS.md` - VPS deployment and operations

