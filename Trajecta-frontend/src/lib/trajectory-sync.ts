import type { FlightFrame } from "@/types/flight";

function toRad(v: number): number {
  return (v * Math.PI) / 180;
}

function haversineMeters(aLat: number, aLon: number, bLat: number, bLon: number): number {
  const r = 6371000;
  const dLat = toRad(bLat - aLat);
  const dLon = toRad(bLon - aLon);
  const p1 = toRad(aLat);
  const p2 = toRad(bLat);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(p1) * Math.cos(p2) * Math.sin(dLon / 2) ** 2;
  return 2 * r * Math.asin(Math.min(1, Math.sqrt(h)));
}

function isValidLatLon(lat: number | undefined, lon: number | undefined): lat is number {
  return Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat as number) <= 90 && Math.abs(lon as number) <= 180;
}

function interpolateReferenceAt(reference: FlightFrame[], t: number): FlightFrame | undefined {
  if (!reference.length) {
    return undefined;
  }
  if (t <= reference[0].t) {
    return reference[0];
  }
  const last = reference[reference.length - 1];
  if (t >= last.t) {
    return last;
  }

  let lo = 0;
  let hi = reference.length - 1;
  while (lo <= hi) {
    const mid = Math.floor((lo + hi) / 2);
    if (reference[mid].t < t) {
      lo = mid + 1;
    } else {
      hi = mid - 1;
    }
  }

  const b = reference[Math.min(lo, reference.length - 1)];
  const a = reference[Math.max(0, lo - 1)];
  const dt = Math.max(1e-6, b.t - a.t);
  const alpha = (t - a.t) / dt;

  return {
    ...a,
    t,
    lat: a.lat + (b.lat - a.lat) * alpha,
    lon: a.lon + (b.lon - a.lon) * alpha,
    alt: a.alt + (b.alt - a.alt) * alpha,
  };
}

export function buildSynchronizedReferenceFrames(frames: FlightFrame[]): FlightFrame[] {
  const insFrames = frames
    .filter((f) => Number.isFinite(f.t) && isValidLatLon(f.lat, f.lon))
    .sort((a, b) => a.t - b.t);

  if (!insFrames.length) {
    return [];
  }

  const rawReference = frames
    .filter((f) => Number.isFinite(f.t) && isValidLatLon(f.gpsLat, f.gpsLon))
    .map((f) => ({
      ...f,
      lat: f.gpsLat as number,
      lon: f.gpsLon as number,
      alt: Number.isFinite(f.gpsAlt) ? (f.gpsAlt as number) : f.alt,
    }))
    .sort((a, b) => a.t - b.t);

  if (!rawReference.length) {
    return [];
  }

  const dedupReference: FlightFrame[] = [];
  for (const frame of rawReference) {
    const prev = dedupReference[dedupReference.length - 1];
    if (!prev || Math.abs(frame.t - prev.t) > 1e-6) {
      dedupReference.push(frame);
    }
  }

  const minT = insFrames[0].t;
  const maxT = insFrames[insFrames.length - 1].t;
  const withinTime = dedupReference.filter((f) => f.t >= minT - 0.5 && f.t <= maxT + 0.5);
  const timeWindowed = withinTime.length >= 3 ? withinTime : dedupReference;

  const filtered: FlightFrame[] = [];
  for (const frame of timeWindowed) {
    const prev = filtered[filtered.length - 1];
    if (!prev) {
      filtered.push(frame);
      continue;
    }

    const dt = frame.t - prev.t;
    if (dt <= 1e-3) {
      continue;
    }

    const speed = haversineMeters(prev.lat, prev.lon, frame.lat, frame.lon) / dt;
    if (!Number.isFinite(speed) || speed > 140) {
      continue;
    }

    filtered.push(frame);
  }

  if (!filtered.length) {
    return [];
  }

  const insStart = insFrames[0];
  const refAtStart = interpolateReferenceAt(filtered, insStart.t) ?? filtered[0];

  const dLat = insStart.lat - refAtStart.lat;
  const dLon = insStart.lon - refAtStart.lon;
  const dAlt = (Number.isFinite(insStart.alt) ? insStart.alt : 0) - (Number.isFinite(refAtStart.alt) ? refAtStart.alt : 0);

  return filtered.map((f) => ({
    ...f,
    lat: f.lat + dLat,
    lon: f.lon + dLon,
    alt: (Number.isFinite(f.alt) ? f.alt : 0) + dAlt,
  }));
}
