import { useMemo } from "react";
import {
  ApiClientError,
  addAiConclusion,
  createTask,
  downloadTrajectory,
  getTask,
  reanalyzeTask,
  regenerateAiConclusion
} from "@/lib/api";
import { API_ERROR_CODES } from "@/lib/error-codes";
import { MAX_BIN_UPLOAD_MB, validateBinTelemetryFile } from "@/lib/upload";
import { useFlightStore } from "@/store/flight-store";
import type { FlightLogData, TaskInfo } from "@/types/flight";

const MAX_REASONABLE_SPEED_MPS = 80;

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

function asNumber(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function asObject(value: unknown): Record<string, unknown> | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return null;
  }
  return value as Record<string, unknown>;
}

function isFiniteNumber(value: number | undefined): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

function toRadians(value: number) {
  return (value * Math.PI) / 180;
}

function haversineMeters(aLat: number, aLon: number, bLat: number, bLon: number) {
  const radius = 6371000;
  const dPhi = toRadians(bLat - aLat);
  const dLambda = toRadians(bLon - aLon);
  const phi1 = toRadians(aLat);
  const phi2 = toRadians(bLat);
  const h =
    Math.sin(dPhi / 2) ** 2 +
    Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) ** 2;
  return 2 * radius * Math.asin(Math.min(1, Math.sqrt(h)));
}

function normalizeLat(raw: number | undefined): number | undefined {
  if (!isFiniteNumber(raw)) {
    return undefined;
  }
  const normalized = Math.abs(raw) > 180 ? raw / 1e7 : raw;
  return Math.abs(normalized) <= 90 ? normalized : undefined;
}

function normalizeLon(raw: number | undefined): number | undefined {
  if (!isFiniteNumber(raw)) {
    return undefined;
  }
  const normalized = Math.abs(raw) > 180 ? raw / 1e7 : raw;
  return Math.abs(normalized) <= 180 ? normalized : undefined;
}

function normalizeAltitude(raw: number | undefined): number | undefined {
  if (!isFiniteNumber(raw)) {
    return undefined;
  }
  return raw;
}

function normalizeSpeed(raw: number | undefined): number | undefined {
  if (!isFiniteNumber(raw)) {
    return undefined;
  }
  return raw;
}

function isValidCoordinate(lat: number | undefined, lon: number | undefined): boolean {
  return isFiniteNumber(lat) && isFiniteNumber(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180;
}

type RawFrame = Omit<FlightLogData["frames"][number], "lat" | "lon"> & {
  lat?: number;
  lon?: number;
};

function hasTimestamp(frame: RawFrame): boolean {
  return isFiniteNumber(frame.t);
}

function findFirstValidCoordinates(frames: RawFrame[]): { lat: number; lon: number } | null {
  const first = frames.find((frame) => isValidCoordinate(frame.lat, frame.lon));
  if (!first) {
    return null;
  }
  return { lat: first.lat as number, lon: first.lon as number };
}

function deriveMetrics(frames: FlightLogData["frames"]) {
  if (!frames.length) {
    return {
      maxAltitude: undefined,
      maxSpeed: undefined,
      flightDurationSec: 0,
      totalDistanceMeters: 0,
      horizontalDistanceMeters: 0,
      totalDistance3dMeters: 0,
      maxVerticalSpeed: undefined
    };
  }

  let horizontalDistance = 0;
  let distance3d = 0;
  let maxSpeed = 0;
  let maxVertical = 0;
  let maxAltitude = frames[0].alt;

  for (let i = 1; i < frames.length; i += 1) {
    const a = frames[i - 1];
    const b = frames[i];
    const dt = Math.max(1e-3, b.t - a.t);
    const segmentHorizontal = haversineMeters(a.lat, a.lon, b.lat, b.lon);
    const segment3d = Math.hypot(segmentHorizontal, b.alt - a.alt);
    const speedFromTrack = segmentHorizontal / dt;
    if (speedFromTrack <= MAX_REASONABLE_SPEED_MPS * 2) {
      horizontalDistance += segmentHorizontal;
      distance3d += segment3d;
      maxSpeed = Math.max(maxSpeed, speedFromTrack);
    }

    if (isFiniteNumber(b.speed)) {
      maxSpeed = Math.max(maxSpeed, clamp(b.speed, -MAX_REASONABLE_SPEED_MPS * 2, MAX_REASONABLE_SPEED_MPS * 2));
    }
    if (isFiniteNumber(b.climbRate)) {
      maxVertical = Math.max(maxVertical, Math.abs(b.climbRate));
    } else {
      const verticalFromAlt = Math.abs((b.alt - a.alt) / dt);
      if (Number.isFinite(verticalFromAlt)) {
        maxVertical = Math.max(maxVertical, verticalFromAlt);
      }
    }
    maxAltitude = Math.max(maxAltitude, b.alt);
  }

  return {
    maxAltitude,
    maxSpeed,
    flightDurationSec: Math.max(0, frames[frames.length - 1].t - frames[0].t),
    totalDistanceMeters: horizontalDistance,
    horizontalDistanceMeters: horizontalDistance,
    totalDistance3dMeters: distance3d,
    maxVerticalSpeed: maxVertical
  };
}

function normalizeMetrics(
  input: Record<string, unknown> | null,
  fallback: ReturnType<typeof deriveMetrics>,
  imuRateHzFallback?: number,
  imuSamplingPeriodMsFallback?: number
) {
  const maxAltitude = asNumber(input?.maxAltitude) ?? fallback.maxAltitude;
  const maxSpeed =
    asNumber(input?.maxSpeed) ??
    asNumber(input?.maxHorizontalSpeed) ??
    fallback.maxSpeed;
  const flightDurationSec =
    asNumber(input?.flightDuration) ??
    asNumber(input?.flightDurationSec) ??
    asNumber(input?.totalFlightDuration) ??
    fallback.flightDurationSec;
  const horizontalDistanceMeters =
    asNumber(input?.horizontalDistanceMeters) ??
    asNumber(input?.horizontalDistanceM) ??
    asNumber(input?.distance2d) ??
    asNumber(input?.distance) ??
    asNumber(input?.totalDistanceMeters) ??
    fallback.horizontalDistanceMeters ??
    fallback.totalDistanceMeters;
  const totalDistance3dMeters =
    asNumber(input?.totalDistance3dMeters) ??
    asNumber(input?.totalDistance3dM) ??
    asNumber(input?.distance3d) ??
    asNumber(input?.distance3dM) ??
    asNumber(input?.totalDistance3d) ??
    asNumber(input?.totalDistance) ??
    fallback.totalDistance3dMeters ??
    horizontalDistanceMeters;
  const totalDistanceMeters = horizontalDistanceMeters;
  const maxVerticalSpeed =
    asNumber(input?.maxVerticalSpeed) ??
    asNumber(input?.maxClimbRate) ??
    fallback.maxVerticalSpeed;
  const endpointErrorM = asNumber(input?.endpointErrorM);
  const rmsCrosstrackErrorM = asNumber(input?.rmsCrosstrackErrorM);
  const ateM = asNumber(input?.ateM);
  const p95CrosstrackErrorM = asNumber(input?.p95CrosstrackErrorM);
  const pointCount = asNumber(input?.pointCount);
  const groundTruthUsed =
    typeof input?.groundTruthUsed === "boolean"
      ? input.groundTruthUsed
      : undefined;
  const imuRateHz =
    asNumber(input?.imuRateHz) ??
    asNumber(input?.imuSamplingHz) ??
    asNumber(input?.imuSamplingRateHz) ??
    asNumber(input?.imu_rate_hz) ??
    asNumber(input?.imu_sampling_hz) ??
    imuRateHzFallback;
  const imuSamplingPeriodMs =
    asNumber(input?.imuSamplingPeriodMs) ??
    asNumber(input?.imuDtMs) ??
    asNumber(input?.imu_sampling_period_ms) ??
    imuSamplingPeriodMsFallback ??
    (isFiniteNumber(imuRateHz) && imuRateHz > 0 ? 1000 / imuRateHz : undefined);
  const usedImuCount =
    asNumber(input?.usedImuCount) ??
    asNumber(input?.used_imu_count);
  const usedBaroCount =
    asNumber(input?.usedBaroCount) ??
    asNumber(input?.used_baro_count);
  const maxSensorGpsErrorM = asNumber(input?.maxSensorGpsErrorM);
  const meanSensorGpsErrorM = asNumber(input?.meanSensorGpsErrorM);
  const peakAbsAccelM =
    asNumber(input?.peakAbsAccelM) ??
    asNumber(input?.peak_abs_accel);
  const peakAbsGyroRadSec =
    asNumber(input?.peakAbsGyroRadSec) ??
    asNumber(input?.peakAbsGyro) ??
    asNumber(input?.peak_abs_gyro);
  const maxVibration =
    asNumber(input?.maxVibration) ??
    asNumber(input?.max_vibration);

  return {
    maxAltitude,
    maxSpeed,
    flightDurationSec,
    totalDistanceMeters,
    horizontalDistanceMeters,
    totalDistance3dMeters,
    maxVerticalSpeed,
    imuRateHz,
    imuSamplingPeriodMs,
    usedImuCount,
    usedBaroCount,
    endpointErrorM,
    rmsCrosstrackErrorM,
    ateM,
    p95CrosstrackErrorM,
    pointCount,
    groundTruthUsed,
    maxSensorGpsErrorM,
    meanSensorGpsErrorM,
    peakAbsAccelM,
    peakAbsGyroRadSec,
    maxVibration
  };
}

function normalizeWorkerTrajectory(raw: unknown): FlightLogData {
  const input = asObject(raw) ?? {};
  const framesRaw = Array.isArray(input.frames) ? input.frames : [];
  const eventsRaw = Array.isArray(input.events) ? input.events : [];
  const metaRaw = asObject(input.meta) ?? {};
  const parsingRaw = asObject(metaRaw.parsing) ?? {};
  const messagesRaw = asObject(parsingRaw.messages) ?? {};
  const imuRaw = asObject(messagesRaw.imu) ?? {};
  const metricsRaw = asObject(input.metrics);
  const imuSamplingHz =
    asNumber(imuRaw.samplingHz) ??
    asNumber(imuRaw.rateHz) ??
    asNumber(imuRaw.frequencyHz);
  const imuSamplingPeriodMs =
    asNumber(imuRaw.samplingPeriodMs) ??
    (isFiniteNumber(imuSamplingHz) && imuSamplingHz > 0 ? 1000 / imuSamplingHz : undefined);
  const imuResampledStepMs =
    asNumber(imuRaw.resampledStepMs) ??
    asNumber(imuRaw.stepMs);

  const parsedFrames = framesRaw.map((frame): RawFrame => {
    const src = asObject(frame) ?? {};
    const pos = asObject(src.pos) ?? {};
    const att = asObject(src.att) ?? {};
    const imu = asObject(src.imu) ?? {};
    const accel = Array.isArray(imu.accel) ? imu.accel : [];
    const gyro = Array.isArray(imu.gyro) ? imu.gyro : [];
    const gps = asObject(src.gps) ?? {};

    return {
      t: asNumber(src.t) ?? Number.NaN,
      lat: normalizeLat(asNumber(pos.lat)),
      lon: normalizeLon(asNumber(pos.lon)),
      alt: normalizeAltitude(asNumber(pos.alt)) ?? 0,
      speed: normalizeSpeed(asNumber(src.vel)),
      battery: asNumber(src.battery),
      roll: asNumber(att.roll),
      pitch: asNumber(att.pitch),
      yaw: asNumber(att.yaw),
      accelX: asNumber(accel[0]),
      accelY: asNumber(accel[1]),
      accelZ: asNumber(accel[2]),
      gyroX: asNumber(gyro[0]),
      gyroY: asNumber(gyro[1]),
      gyroZ: asNumber(gyro[2]),
      vibration: asNumber(src.vibration),
      pressure: asNumber(src.pressure),
      gpsLat: normalizeLat(asNumber(gps.lat)),
      gpsLon: normalizeLon(asNumber(gps.lon)),
      gpsAlt: normalizeAltitude(asNumber(gps.alt)),
      positionErrorM: asNumber(src.positionErrorM),
      climbRate: normalizeSpeed(asNumber(src.climbRate))
    };
  });

  const timedFrames = parsedFrames
    .filter(hasTimestamp)
    .sort((a, b) => (a.t as number) - (b.t as number));

  const firstValidCoordinates = findFirstValidCoordinates(timedFrames);
  let lastLat = firstValidCoordinates?.lat ?? 0;
  let lastLon = firstValidCoordinates?.lon ?? 0;

  const t0 = timedFrames[0]?.t ?? 0;
  const frames: FlightLogData["frames"] = timedFrames.map((frame, index, arr) => {
    const hasCoord = isValidCoordinate(frame.lat, frame.lon);
    if (hasCoord) {
      lastLat = frame.lat as number;
      lastLon = frame.lon as number;
    }

    const base = (frame.t as number) - t0;
    const prev = index > 0 ? arr[index - 1].t - t0 : -1;
    const t = base <= prev ? prev + 1e-3 : base;

    return {
      ...frame,
      lat: hasCoord ? (frame.lat as number) : lastLat,
      lon: hasCoord ? (frame.lon as number) : lastLon,
      t
    };
  });

  const events = eventsRaw.map((event): FlightLogData["events"][number] => {
    const src = asObject(event) ?? {};
    const rawT = asNumber(src.t);
    const description = typeof src.description === "string" ? src.description : undefined;
    const fallbackValue =
      typeof src.value === "string" ||
      typeof src.value === "number" ||
      typeof src.value === "boolean" ||
      src.value === null
        ? src.value
        : undefined;
    return {
      t: isFiniteNumber(rawT) ? Math.max(0, rawT - t0) : 0,
      type: typeof src.type === "string" ? src.type : "EVENT",
      eventId: asNumber(src.eventId),
      code: typeof src.code === "string" ? src.code : undefined,
      severity: typeof src.severity === "string" ? src.severity : undefined,
      message: description ?? (typeof fallbackValue === "string" ? fallbackValue : undefined),
      value: fallbackValue
    };
  });

  return {
    metadata: {
      parserVersion: "trajecta-worker",
      source: "api",
      logName: typeof metaRaw.logName === "string" ? metaRaw.logName : undefined,
      imuSamplingHz,
      imuSamplingPeriodMs,
      imuResampledStepMs,
      gpsUnits: "deg/WGS84, m, m/s"
    },
    frames,
    events,
    params: asObject(input.params) as FlightLogData["params"] ?? {},
    metrics: normalizeMetrics(metricsRaw, deriveMetrics(frames), imuSamplingHz, imuSamplingPeriodMs),
    aiConclusion: typeof input.aiConclusion === "string" ? input.aiConclusion : undefined,
    aiModel: typeof input.aiModel === "string" ? input.aiModel : undefined
  };
}

function apiErrorMessage(error: unknown): string {
  if (error instanceof ApiClientError) {
    if (error.code === API_ERROR_CODES.FILE_TOO_LARGE || error.status === 413) {
      return `BIN file exceeds maximum size of ${MAX_BIN_UPLOAD_MB}MB`;
    }
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Unknown error";
}

function extractAiConclusionFromText(content: string): string | undefined {
  const marker = "AI Conclusion:";
  const idx = content.lastIndexOf(marker);
  if (idx < 0) {
    return undefined;
  }
  const value = content.slice(idx + marker.length).trim();
  return value || undefined;
}

export function parseTrajectory(content: string): FlightLogData {
  try {
    return normalizeWorkerTrajectory(JSON.parse(content));
  } catch {
    return {
      metadata: { source: "api", parserVersion: "fallback" },
      frames: [],
      events: [],
      params: {},
      metrics: {},
      aiConclusion: extractAiConclusionFromText(content)
    };
  }
}

function validateUploadFile(file: File): string | null {
  const validation = validateBinTelemetryFile(file);
  if (validation === "invalid_extension") {
    return "Only .bin telemetry files are supported";
  }
  if (validation === "too_large") {
    return `BIN file exceeds maximum size of ${MAX_BIN_UPLOAD_MB}MB`;
  }
  return null;
}

type BatchProgress = {
  total: number;
  submitted: number;
  failed: number;
  currentFileName: string;
};

type BatchResult = {
  total: number;
  submitted: number;
  failed: string[];
};

function buildTaskTitle(baseTitle: string, file: File, index: number, total: number) {
  const normalizedBase = baseTitle.trim();
  if (!normalizedBase) {
    return file.name;
  }
  if (total === 1) {
    return normalizedBase;
  }
  return `${normalizedBase} (${index + 1}/${total})`;
}

export function useFlightData() {
  const auth = useFlightStore((s) => s.auth);
  const data = useFlightStore((s) => s.data);
  const setData = useFlightStore((s) => s.setData);
  const loading = useFlightStore((s) => s.loading);
  const setLoading = useFlightStore((s) => s.setLoading);
  const setCurrentTask = useFlightStore((s) => s.setCurrentTask);
  const setTaskStatus = useFlightStore((s) => s.setTaskStatus);
  const setError = useFlightStore((s) => s.setError);
  const setMode = useFlightStore((s) => s.setMode);
  const logout = useFlightStore((s) => s.logout);
  const currentTask = useFlightStore((s) => s.currentTask);

  const maxTimeSec = useMemo(() => {
    const frames = data?.frames ?? [];
    if (!frames.length) {
      return 0;
    }
    return frames[frames.length - 1].t;
  }, [data]);

  async function loadFromBin(file: File, title: string) {
    if (!auth.isAuthenticated || !auth.token) {
      setError("Authentication required", "tasks");
      return;
    }

    const uploadValidationError = validateUploadFile(file);
    if (uploadValidationError) {
      setError(uploadValidationError, "tasks");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const created = await createTask({ token: auth.token, file, title });
      setCurrentTask({
        id: created.id,
        title: created.title,
        folderName: created.folderName ?? null,
        folderId: created.folderId ?? null,
        status: created.status,
        sourceTaskId: created.sourceTaskId ?? null
      });
      setTaskStatus(created.status);
      setData(null);
      setMode("tasks");
      return created;
    } catch (error) {
      const message = apiErrorMessage(error);
      setError(message, "tasks");
      if (error instanceof ApiClientError && error.status === 401) {
        logout();
      }
      return;
    } finally {
      setLoading(false);
    }
  }

  async function loadFromBinBatch(
    files: File[],
    titleBase: string,
    folderId?: number,
    onProgress?: (progress: BatchProgress) => void
  ): Promise<BatchResult> {
    if (!auth.isAuthenticated || !auth.token) {
      setError("Authentication required", "tasks");
      return { total: files.length, submitted: 0, failed: files.map((file) => file.name) };
    }

    const validFiles: File[] = [];
    const failed: string[] = [];

    for (const file of files) {
      const uploadValidationError = validateUploadFile(file);
      if (uploadValidationError) {
        failed.push(file.name);
        continue;
      }
      validFiles.push(file);
    }

    if (validFiles.length === 0) {
      setError(`Failed to queue ${failed.length} file(s): ${failed.join(", ")}`, "tasks");
      return { total: files.length, submitted: 0, failed };
    }

    setLoading(true);
    setError(null);
    let submitted = 0;

    try {
      for (let i = 0; i < validFiles.length; i += 1) {
        const file = validFiles[i];
        onProgress?.({ total: validFiles.length, submitted, failed: failed.length, currentFileName: file.name });

        try {
          const created = await createTask({
            token: auth.token,
            file,
            title: buildTaskTitle(titleBase, file, i, validFiles.length),
            folderId
          });
          submitted += 1;
          setCurrentTask({
            id: created.id,
            title: created.title,
            folderName: created.folderName ?? null,
            folderId: created.folderId ?? null,
            status: created.status,
            sourceTaskId: created.sourceTaskId ?? null
          });
          setTaskStatus(created.status);
        } catch (error) {
          failed.push(file.name);
          if (error instanceof ApiClientError && error.status === 401) {
            logout();
            break;
          }
        }
      }

      setData(null);
      setMode("tasks");

      if (failed.length > 0) {
        setError(`Failed to queue ${failed.length} file(s): ${failed.join(", ")}`, "tasks");
      }

      return { total: files.length, submitted, failed };
    } finally {
      setLoading(false);
      onProgress?.({ total: validFiles.length, submitted, failed: failed.length, currentFileName: "" });
    }
  }

  async function selectTask(task: TaskInfo) {
    if (!auth.isAuthenticated || !auth.token) {
      setError("Authentication required", "tasks");
      return false;
    }

    setCurrentTask(task);
    setTaskStatus(task.status, task.errorMessage);

    if (task.status !== "COMPLETED") {
      setData(null);
      setMode("tasks");
      return true;
    }

    setLoading(true);
    setError(null);
    try {
      const freshTask = await getTask({ token: auth.token, taskId: task.id });
      setCurrentTask(freshTask);
      setTaskStatus(freshTask.status, freshTask.errorMessage);

      const trajectory = await downloadTrajectory({ token: auth.token, taskId: task.id });
      const normalized = parseTrajectory(trajectory);
      normalized.aiConclusion = normalized.aiConclusion ?? freshTask.aiConclusion ?? undefined;
      normalized.aiModel = normalized.aiModel ?? freshTask.aiModel ?? undefined;
      setData(normalized);
      setMode("analytics");
      return true;
    } catch (error) {
      const message = apiErrorMessage(error);
      setError(message, "tasks");
      if (error instanceof ApiClientError && error.status === 401) {
        logout();
      }
      return false;
    } finally {
      setLoading(false);
    }
  }

  async function requestAiConclusion(force = false) {
    if (!auth.isAuthenticated || !auth.token || !currentTask) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const task = force
        ? await regenerateAiConclusion({ token: auth.token, taskId: currentTask.id })
        : await addAiConclusion({ token: auth.token, taskId: currentTask.id });

      setCurrentTask(task);
      setTaskStatus(task.status, task.errorMessage);

      const trajectory = await downloadTrajectory({ token: auth.token, taskId: currentTask.id });
      const normalized = parseTrajectory(trajectory);
      normalized.aiConclusion = normalized.aiConclusion ?? task.aiConclusion ?? undefined;
      normalized.aiModel = normalized.aiModel ?? task.aiModel ?? undefined;
      setData(normalized);
      return true;
    } catch (error) {
      const message = apiErrorMessage(error);
      setError(message, "tasks");
      if (error instanceof ApiClientError && error.status === 401) {
        logout();
      }
      return false;
    } finally {
      setLoading(false);
    }
  }

  async function rerunTaskWithParams(input: {
    taskId: number;
    title?: string;
    folderName?: string;
    folderId?: number;
    timelineDtSeconds?: number;
  }) {
    if (!auth.isAuthenticated || !auth.token) {
      setError("Authentication required", "tasks");
      return false;
    }

    setLoading(true);
    setError(null);
    try {
      const created = await reanalyzeTask({
        token: auth.token,
        taskId: input.taskId,
        title: input.title,
        folderName: input.folderName,
        folderId: input.folderId,
        timelineDtSeconds: input.timelineDtSeconds
      });
      setCurrentTask({
        id: created.id,
        title: created.title,
        folderName: created.folderName ?? null,
        folderId: created.folderId ?? null,
        status: created.status,
        sourceTaskId: created.sourceTaskId ?? null
      });
      setTaskStatus(created.status);
      setData(null);
      setMode("tasks");
      return true;
    } catch (error) {
      const message = apiErrorMessage(error);
      setError(message, "tasks");
      if (error instanceof ApiClientError && error.status === 401) {
        logout();
      }
      return false;
    } finally {
      setLoading(false);
    }
  }

  return {
    data,
    loading,
    maxTimeSec,
    loadFromBin,
    loadFromBinBatch,
    selectTask,
    requestAiConclusion,
    rerunTaskWithParams,
    clear: () => setData(null)
  };
}
