import {
  Cartesian3,
  Color,
  HeadingPitchRoll,
  JulianDate,
  Math as CesiumMath,
  Quaternion,
  Transforms,
  type Matrix4
} from "cesium";
import type { FlightFrame } from "@/types/flight";

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

function lerpOptional(a: number | undefined, b: number | undefined, alpha: number): number | undefined {
  const hasA = Number.isFinite(a);
  const hasB = Number.isFinite(b);
  if (hasA && hasB) {
    return CesiumMath.lerp(a as number, b as number, alpha);
  }
  if (hasA) {
    return a;
  }
  if (hasB) {
    return b;
  }
  return undefined;
}

export function findFramePair(frames: FlightFrame[], t: number): [FlightFrame, FlightFrame, number] {
  if (frames.length === 0) {
    const empty: FlightFrame = { t: 0, lat: 0, lon: 0, alt: 0 };
    return [empty, empty, 0];
  }
  if (t <= frames[0].t) {
    return [frames[0], frames[0], 0];
  }
  const last = frames[frames.length - 1];
  if (t >= last.t) {
    return [last, last, 0];
  }

  let lo = 0;
  let hi = frames.length - 1;
  while (lo <= hi) {
    const mid = Math.floor((lo + hi) / 2);
    if (frames[mid].t < t) {
      lo = mid + 1;
    } else {
      hi = mid - 1;
    }
  }

  const nextIdx = Math.min(lo, frames.length - 1);
  const prevIdx = Math.max(nextIdx - 1, 0);
  const a = frames[prevIdx];
  const b = frames[nextIdx];
  const span = Math.max(1e-6, b.t - a.t);
  const alpha = (t - a.t) / span;
  return [a, b, alpha];
}

export function interpolateFrame(frames: FlightFrame[], t: number): FlightFrame {
  const [a, b, alpha] = findFramePair(frames, t);

  const qa = Quaternion.fromHeadingPitchRoll(
    new HeadingPitchRoll(
      CesiumMath.toRadians(a.yaw ?? 0),
      CesiumMath.toRadians(a.pitch ?? 0),
      CesiumMath.toRadians(a.roll ?? 0)
    )
  );
  const qb = Quaternion.fromHeadingPitchRoll(
    new HeadingPitchRoll(
      CesiumMath.toRadians(b.yaw ?? 0),
      CesiumMath.toRadians(b.pitch ?? 0),
      CesiumMath.toRadians(b.roll ?? 0)
    )
  );
  const qm = Quaternion.slerp(qa, qb, alpha, new Quaternion());
  const hpr = HeadingPitchRoll.fromQuaternion(qm);

  return {
    t,
    lat: CesiumMath.lerp(a.lat, b.lat, alpha),
    lon: CesiumMath.lerp(a.lon, b.lon, alpha),
    alt: CesiumMath.lerp(a.alt, b.alt, alpha),
    speed: CesiumMath.lerp(a.speed ?? 0, b.speed ?? 0, alpha),
    battery: lerpOptional(a.battery, b.battery, alpha),
    accelX: CesiumMath.lerp(a.accelX ?? 0, b.accelX ?? 0, alpha),
    accelY: CesiumMath.lerp(a.accelY ?? 0, b.accelY ?? 0, alpha),
    accelZ: CesiumMath.lerp(a.accelZ ?? 0, b.accelZ ?? 0, alpha),
    roll: CesiumMath.toDegrees(hpr.roll),
    pitch: CesiumMath.toDegrees(hpr.pitch),
    yaw: CesiumMath.toDegrees(hpr.heading),
    climbRate: CesiumMath.lerp(a.climbRate ?? 0, b.climbRate ?? 0, alpha)
  };
}

export function speedToColor(speed = 0, maxSpeed = 30) {
  const n = Math.min(1, Math.max(0, speed / Math.max(1, maxSpeed)));
  return Color.fromHsl((0.66 - n * 0.66) * 0.8, 0.9, 0.55, 0.9);
}

export function positionFromFrame(frame: FlightFrame) {
  const lat = Number.isFinite(frame.lat) ? clamp(frame.lat, -90, 90) : 0;
  const lon = Number.isFinite(frame.lon) ? clamp(frame.lon, -180, 180) : 0;
  const alt = Number.isFinite(frame.alt) ? frame.alt : 0;
  return Cartesian3.fromDegrees(lon, lat, alt);
}

export function droneModelMatrix(frame: FlightFrame, result?: Matrix4) {
  const pos = positionFromFrame(frame);
  const hpr = new HeadingPitchRoll(
    CesiumMath.toRadians(frame.yaw ?? 0),
    CesiumMath.toRadians(frame.pitch ?? 0),
    CesiumMath.toRadians(frame.roll ?? 0)
  );
  return Transforms.headingPitchRollToFixedFrame(pos, hpr, undefined, undefined, result);
}

export function toJulian(seconds: number) {
  return JulianDate.addSeconds(JulianDate.now(), seconds, new JulianDate());
}

export function buildRelativeTrajectory(frames: FlightFrame[]): FlightFrame[] {
  if (frames.length <= 1) {
    return frames;
  }

  const start = frames[0];
  const startLat = Number.isFinite(start.lat) ? start.lat : 0;
  const startLon = Number.isFinite(start.lon) ? start.lon : 0;
  const startAlt = Number.isFinite(start.alt) ? start.alt : 0;

  const earthRadius = 6378137;
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const toDeg = (rad: number) => (rad * 180) / Math.PI;
  const normalizeHeadingDelta = (deltaDeg: number) => {
    let delta = deltaDeg;
    while (delta > 180) {
      delta -= 360;
    }
    while (delta < -180) {
      delta += 360;
    }
    return delta;
  };
  const geodesicDistance = (a: FlightFrame, b: FlightFrame) => {
    const r = 6371000;
    const dLat = toRad(b.lat - a.lat);
    const dLon = toRad(b.lon - a.lon);
    const lat1 = toRad(a.lat);
    const lat2 = toRad(b.lat);
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
    return 2 * r * Math.asin(Math.min(1, Math.sqrt(h)));
  };

  let east = 0;
  let north = 0;
  let up = 0;
  let forwardSpeed = Number.isFinite(start.speed) ? clamp(start.speed ?? 0, 0, 120) : 0;
  let velUp = 0;
  let headingDeg = Number.isFinite(start.yaw) ? (start.yaw ?? 0) : 0;

  const out: FlightFrame[] = [{ ...start }];

  for (let i = 1; i < frames.length; i += 1) {
    const prev = frames[i - 1];
    const current = frames[i];

    const dt = clamp(current.t - prev.t, 1e-3, 1.0);
    const rawYaw = Number.isFinite(current.yaw) ? (current.yaw ?? headingDeg) : headingDeg;
    const yawStep = normalizeHeadingDelta(rawYaw - headingDeg);
    headingDeg += yawStep * 0.35;
    const heading = toRad(headingDeg);

    const sinYaw = Math.sin(heading);
    const cosYaw = Math.cos(heading);

    const ax = Number.isFinite(current.accelX) ? (current.accelX ?? 0) : 0;
    const ay = Number.isFinite(current.accelY) ? (current.accelY ?? 0) : 0;
    const forwardAcc = clamp(ax * cosYaw + ay * sinYaw, -18, 18);
    const predictedSpeed = clamp(forwardSpeed + forwardAcc * dt, 0, 120);

    const gpsSegmentSpeed = geodesicDistance(prev, current) / dt;
    const measuredSpeed = Number.isFinite(current.speed)
      ? clamp(Math.abs(current.speed ?? 0), 0, 120)
      : Number.isFinite(gpsSegmentSpeed) && gpsSegmentSpeed < 120
        ? gpsSegmentSpeed
        : null;

    if (measuredSpeed !== null) {
      const disagreement = Math.abs(predictedSpeed - measuredSpeed);
      const trust = clamp(0.2 + disagreement / 30, 0.2, 0.6);
      forwardSpeed = predictedSpeed * (1 - trust) + measuredSpeed * trust;
    } else {
      forwardSpeed = predictedSpeed * 0.995;
    }

    const velEast = forwardSpeed * sinYaw;
    const velNorth = forwardSpeed * cosYaw;

    if (Number.isFinite(current.climbRate)) {
      velUp = clamp(current.climbRate ?? 0, -40, 40);
    } else {
      const az = Number.isFinite(current.accelZ) ? current.accelZ ?? 0 : 0;
      velUp = clamp((velUp + az * dt) * 0.98, -40, 40);
    }

    east += velEast * dt;
    north += velNorth * dt;
    up += velUp * dt;

    const lat = startLat + toDeg(north / earthRadius);
    const lon = startLon + toDeg(east / (earthRadius * Math.max(1e-6, Math.cos(toRad(startLat)))));
    const alt = startAlt + up;

    out.push({
      ...current,
      lat,
      lon,
      alt,
      speed: forwardSpeed
    });
  }

  return out;
}

export function buildHybridTrajectory(frames: FlightFrame[]): FlightFrame[] {
  if (frames.length <= 1) {
    return frames;
  }

  const relative = buildRelativeTrajectory(frames);
  const out: FlightFrame[] = [{ ...frames[0] }];

  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const hav = (a: FlightFrame, b: FlightFrame) => {
    const r = 6371000;
    const dLat = toRad(b.lat - a.lat);
    const dLon = toRad(b.lon - a.lon);
    const lat1 = toRad(a.lat);
    const lat2 = toRad(b.lat);
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
    return 2 * r * Math.asin(Math.min(1, Math.sqrt(h)));
  };

  const gpsTrustHistory: number[] = [];

  for (let i = 1; i < frames.length; i += 1) {
    const gps = frames[i];
    const gpsPrev = frames[i - 1];
    const ins = relative[i];
    const insPrev = relative[i - 1];

    const dt = clamp(gps.t - gpsPrev.t, 1e-3, 0.5);
    const gpsSpeed = hav(gpsPrev, gps) / dt;
    const insSpeed = hav(insPrev, ins) / dt;
    const speedHint = Number.isFinite(gps.speed) ? clamp(Math.abs(gps.speed ?? 0), 0, 160) : gpsSpeed;

    const gpsLooksBad = gpsSpeed > 95 || gpsSpeed > speedHint * 2.5 + 10;
    const insLooksBad = insSpeed > 140;

    let gpsTrust = 0.72;
    if (gpsLooksBad) {
      gpsTrust = 0.1;
    } else if (insLooksBad) {
      gpsTrust = 0.92;
    } else {
      const disagreement = hav(gps, ins);
      gpsTrust = clamp(0.88 - disagreement / 180, 0.25, 0.9);
    }

    gpsTrustHistory.push(gpsTrust);
    const recent = gpsTrustHistory.slice(-12);
    const smoothedTrust = recent.reduce((acc, value) => acc + value, 0) / recent.length;

    const lat = gps.lat * smoothedTrust + ins.lat * (1 - smoothedTrust);
    const lon = gps.lon * smoothedTrust + ins.lon * (1 - smoothedTrust);
    const alt = gps.alt * smoothedTrust + ins.alt * (1 - smoothedTrust);

    out.push({
      ...gps,
      lat: Number.isFinite(lat) ? lat : gps.lat,
      lon: Number.isFinite(lon) ? lon : gps.lon,
      alt: Number.isFinite(alt) ? alt : gps.alt
    });
  }

  return out;
}
