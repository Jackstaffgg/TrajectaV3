export type XYPoint = [number, number];

export type GeoOrigin = {
  lat: number;
  lon: number;
  alt: number;
};

export type GeoLike = {
  lat: number;
  lon: number;
  alt: number;
};

const EARTH_RADIUS_M = 6378137;

export function toPlanarMeters(lat: number, lon: number, refLat: number, refLon: number): XYPoint {
  const latRad = (lat * Math.PI) / 180;
  const refLatRad = (refLat * Math.PI) / 180;
  const dLat = latRad - refLatRad;
  const dLon = ((lon - refLon) * Math.PI) / 180;
  const east = dLon * EARTH_RADIUS_M * Math.max(1e-6, Math.cos(refLatRad));
  const north = dLat * EARTH_RADIUS_M;
  return [east, north];
}

export function toLocalMeters3D(point: GeoLike, origin: GeoOrigin): { x: number; y: number; z: number } {
  const [x, y] = toPlanarMeters(point.lat, point.lon, origin.lat, origin.lon);
  return {
    x,
    y,
    z: (Number.isFinite(point.alt) ? point.alt : origin.alt) - origin.alt,
  };
}

export function equalBounds(
  tracks: XYPoint[][],
  aspectRatio: number,
): { xMin: number; xMax: number; yMin: number; yMax: number } {
  const points = tracks.flat();
  if (!points.length) {
    return { xMin: -10, xMax: 10, yMin: -10, yMax: 10 };
  }

  let minX = Number.POSITIVE_INFINITY;
  let maxX = Number.NEGATIVE_INFINITY;
  let minY = Number.POSITIVE_INFINITY;
  let maxY = Number.NEGATIVE_INFINITY;
  for (const [x, y] of points) {
    minX = Math.min(minX, x);
    maxX = Math.max(maxX, x);
    minY = Math.min(minY, y);
    maxY = Math.max(maxY, y);
  }

  const centerX = (minX + maxX) / 2;
  const centerY = (minY + maxY) / 2;

  const dx = Math.max(1e-3, maxX - minX);
  const dy = Math.max(1e-3, maxY - minY);
  const safeRatio = Math.max(0.25, Math.min(4.0, aspectRatio));

  const ySpan = Math.max(dy, dx / safeRatio);
  const xSpan = ySpan * safeRatio;
  const halfX = xSpan * 0.55;
  const halfY = ySpan * 0.55;

  return {
    xMin: centerX - halfX,
    xMax: centerX + halfX,
    yMin: centerY - halfY,
    yMax: centerY + halfY,
  };
}

export function formatCoord(value: number): string {
  if (!Number.isFinite(value)) {
    return "-";
  }
  const abs = Math.abs(value);
  if (abs >= 1000) {
    return value.toFixed(0);
  }
  if (abs >= 100) {
    return value.toFixed(1);
  }
  if (abs >= 10) {
    return value.toFixed(2);
  }
  return value.toFixed(3);
}

export function normalizeTrackStart(track: XYPoint[]): XYPoint[] {
  if (!track.length) {
    return track;
  }
  const [startX, startY] = track[0];
  return track.map(([x, y]) => [x - startX, y - startY]);
}
