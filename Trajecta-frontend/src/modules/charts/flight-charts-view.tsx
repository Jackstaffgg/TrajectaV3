import { useEffect, useMemo, useRef, useState } from "react";
import * as echarts from "echarts";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { useLocaleStore } from "@/store/locale-store";
import { useFlightStore } from "@/store/flight-store";
import { tr } from "@/lib/i18n";
import { equalBounds, formatCoord, normalizeTrackStart, toPlanarMeters, type XYPoint } from "@/lib/chart-geometry";

type SeriesDef = {
  label: string;
  color: string;
  accessor: (idx: number) => number | undefined;
  yAxis?: "left" | "right";
};

function finiteOrZero(value: number | undefined) {
  return Number.isFinite(value) ? (value as number) : 0;
}

function finiteOrUndefined(value: number | undefined) {
  return Number.isFinite(value) ? (value as number) : undefined;
}

function finiteOrNaN(value: number | undefined) {
  return Number.isFinite(value) ? (value as number) : Number.NaN;
}

function Trajectory2DChart({
  ins,
  reference
}: {
  ins: XYPoint[];
  reference: XYPoint[];
}) {
  const ref = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!ref.current) {
      return;
    }
    const chart = echarts.init(ref.current, undefined, { renderer: "canvas" });

    const width = Math.max(1, chart.getWidth() - 56);
    const height = Math.max(1, chart.getHeight() - 54);
    const bounds = equalBounds([ins, reference], width / height);

    chart.setOption({
      animation: false,
      backgroundColor: "transparent",
      grid: { left: 40, right: 16, top: 18, bottom: 32 },
      xAxis: {
        type: "value",
        name: "East (m)",
        min: bounds.xMin,
        max: bounds.xMax,
        axisLabel: { formatter: (value: number) => formatCoord(value) },
        axisLine: { lineStyle: { color: "#64748b" } },
      },
      yAxis: {
        type: "value",
        name: "North (m)",
        min: bounds.yMin,
        max: bounds.yMax,
        axisLabel: { formatter: (value: number) => formatCoord(value) },
        axisLine: { lineStyle: { color: "#64748b" } },
        splitLine: { lineStyle: { color: "#1e293b" } },
      },
      tooltip: {
        trigger: "item",
        formatter: (params: unknown) => {
          const p = params as { seriesName?: string; data?: [number, number] };
          const x = p.data?.[0];
          const y = p.data?.[1];
          return `${p.seriesName ?? "Track"}<br/>E: ${formatCoord(Number(x))} m<br/>N: ${formatCoord(Number(y))} m`;
        }
      },
      legend: { top: 0, textStyle: { color: "#94a3b8" } },
      series: [
        {
          name: "INS",
          type: "line",
          showSymbol: false,
          lineStyle: { color: "#22d3ee", width: 2 },
          data: ins
        },
        {
          name: "Reference",
          type: "line",
          showSymbol: false,
          lineStyle: { color: "#f97316", width: 2, type: "dashed" },
          data: reference
        }
      ]
    });

    const handleResize = () => chart.resize();
    window.addEventListener("resize", handleResize);

    return () => {
      window.removeEventListener("resize", handleResize);
      chart.dispose();
    };
  }, [ins, reference]);

  return <div ref={ref} className="mx-auto aspect-square w-full max-w-3xl" />;
}

function TimelineChart({
  id,
  x,
  lines,
  scrubber,
  onHover
}: {
  id: string;
  x: number[];
  lines: SeriesDef[];
  scrubber: number;
  onHover: (t: number) => void;
}) {
  const ref = useRef<HTMLDivElement | null>(null);
  const [windowSize, setWindowSize] = useState(0);
  const [startIndex, setStartIndex] = useState(0);

  const totalPoints = x.length;
  const minWindow = useMemo(() => Math.max(20, Math.floor(totalPoints * 0.05)), [totalPoints]);

  useEffect(() => {
    if (totalPoints <= 0) {
      setWindowSize(0);
      setStartIndex(0);
      return;
    }
    const initialWindow = Math.max(minWindow, Math.floor(totalPoints * 0.3));
    setWindowSize(Math.min(totalPoints, initialWindow));
    setStartIndex(0);
  }, [totalPoints, minWindow]);

  const safeWindowSize = Math.min(totalPoints, Math.max(1, windowSize || totalPoints));
  const maxStart = Math.max(0, totalPoints - safeWindowSize);
  const safeStart = Math.min(Math.max(0, startIndex), maxStart);
  const endIndex = Math.min(totalPoints - 1, safeStart + safeWindowSize - 1);
  const xMin = x[safeStart] ?? 0;
  const xMax = x[endIndex] ?? x[x.length - 1] ?? 0;
  const preparedSeries = useMemo(() => {
    return lines
      .map((line) => {
        const data = x.map((t, idx) => [t, finiteOrNaN(line.accessor(idx))]);
        const hasFiniteValue = data.some((point) => Number.isFinite(point[1]));
        return {
          line,
          data,
          hasFiniteValue,
        };
      })
      .filter((item) => item.hasFiniteValue);
  }, [lines, x]);
  const hasRightAxis = preparedSeries.some((item) => item.line.yAxis === "right");

  function handleZoomIn() {
    if (totalPoints <= 0) {
      return;
    }
    const nextWindow = Math.max(minWindow, Math.floor(safeWindowSize * 0.75));
    const center = safeStart + Math.floor(safeWindowSize / 2);
    const nextStart = Math.max(0, Math.min(totalPoints - nextWindow, center - Math.floor(nextWindow / 2)));
    setWindowSize(nextWindow);
    setStartIndex(nextStart);
  }

  function handleZoomOut() {
    if (totalPoints <= 0) {
      return;
    }
    const nextWindow = Math.min(totalPoints, Math.ceil(safeWindowSize * 1.25));
    const center = safeStart + Math.floor(safeWindowSize / 2);
    const nextStart = Math.max(0, Math.min(totalPoints - nextWindow, center - Math.floor(nextWindow / 2)));
    setWindowSize(nextWindow);
    setStartIndex(nextStart);
  }

  useEffect(() => {
    if (!ref.current) {
      return;
    }
    const chart = echarts.init(ref.current, undefined, { renderer: "canvas" });
    const palette = preparedSeries.map((item) => item.line.color);

    chart.setOption({
      animation: false,
      color: palette,
      backgroundColor: "transparent",
      grid: { left: 40, right: 12, top: 14, bottom: 24 },
      xAxis: { type: "value", min: xMin, max: xMax, axisLine: { lineStyle: { color: "#64748b" } } },
      yAxis: hasRightAxis
        ? [
            { type: "value", position: "left", axisLine: { lineStyle: { color: "#64748b" } }, splitLine: { lineStyle: { color: "#1e293b" } } },
            { type: "value", position: "right", axisLine: { lineStyle: { color: "#64748b" } }, splitLine: { show: false } }
          ]
        : { type: "value", axisLine: { lineStyle: { color: "#64748b" } }, splitLine: { lineStyle: { color: "#1e293b" } } },
      tooltip: {
        trigger: "axis",
        valueFormatter: (value: unknown) => formatCoord(Number(value))
      },
      series: preparedSeries.map(({ line, data }) => ({
        name: line.label,
        type: "line",
        showSymbol: false,
        connectNulls: false,
        smooth: false,
        yAxisIndex: line.yAxis === "right" ? 1 : 0,
        lineStyle: { color: line.color, width: 1.8 },
        data
      }))
    });

    const handleResize = () => chart.resize();
    window.addEventListener("resize", handleResize);

    chart.on("updateAxisPointer", (e: unknown) => {
      const payload = e as { axesInfo?: Array<{ value: number }> };
      const value = payload.axesInfo?.[0]?.value;
      if (typeof value === "number") {
        onHover(value);
      }
    });

    return () => {
      window.removeEventListener("resize", handleResize);
      chart.dispose();
    };
  }, [hasRightAxis, id, onHover, preparedSeries, x, xMax, xMin]);

  useEffect(() => {
    if (!ref.current) {
      return;
    }
    const chart = echarts.getInstanceByDom(ref.current);
    if (!chart) {
      return;
    }
    chart.setOption({
      xAxis: { axisPointer: { value: scrubber, status: "show", snap: true } },
      series: preparedSeries.map(({ line }) => ({
        name: line.label,
        markLine: {
          symbol: "none",
          animation: false,
          lineStyle: { color: "#22d3ee", width: 1, type: "dashed" },
          data: [{ xAxis: scrubber }]
        }
      }))
    });
  }, [scrubber, preparedSeries]);

  if (totalPoints === 0 || preparedSeries.length === 0) {
    return <p className="text-sm text-muted-foreground">No valid data for this chart.</p>;
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2">
        <Button type="button" variant="outline" size="sm" onClick={handleZoomOut}>
          -
        </Button>
        <Button type="button" variant="outline" size="sm" onClick={handleZoomIn}>
          +
        </Button>
        <span className="ml-2 text-xs text-muted-foreground">{safeWindowSize} / {totalPoints}</span>
      </div>
      <div ref={ref} className="h-64 w-full" />
      <input
        type="range"
        min={0}
        max={maxStart}
        value={safeStart}
        onChange={(e) => setStartIndex(Number(e.target.value))}
        className="h-2 w-full cursor-pointer accent-[hsl(var(--accent))]"
      />
    </div>
  );
}

export function FlightChartsView() {
  const data = useFlightStore((s) => s.data);
  const locale = useLocaleStore((s) => s.locale);
  const scrubber = useFlightStore((s) => s.replay.timeSec);
  const setScrubber = useFlightStore((s) => s.setReplayTime);

  const x = useMemo(() => data?.frames.map((f) => f.t) ?? [], [data?.frames]);
  const frames = data?.frames ?? [];
  const trajectoryOrigin = useMemo(() => {
    const first = frames.find((f) => Number.isFinite(f.lat) && Number.isFinite(f.lon));
    if (!first) {
      return { lat: 0, lon: 0 };
    }
    return { lat: first.lat, lon: first.lon };
  }, [frames]);
  const insTrajectory = useMemo(() => {
    const rawIns = frames
      .filter((f) => Number.isFinite(f.lat) && Number.isFinite(f.lon))
      .map((f) => toPlanarMeters(f.lat, f.lon, trajectoryOrigin.lat, trajectoryOrigin.lon));
    return normalizeTrackStart(rawIns);
  }, [frames, trajectoryOrigin.lat, trajectoryOrigin.lon]);
  const referenceTrajectory = useMemo(() => {
    const rawReference = frames
      .filter((f) => Number.isFinite(f.gpsLat) && Number.isFinite(f.gpsLon))
      .map((f) => toPlanarMeters(f.gpsLat as number, f.gpsLon as number, trajectoryOrigin.lat, trajectoryOrigin.lon));
    return normalizeTrackStart(rawReference);
  }, [frames, trajectoryOrigin.lat, trajectoryOrigin.lon]);

  if (!data) {
    return null;
  }

  return (
    <div className="space-y-4">
      <h2 className="text-xl font-semibold">{tr(locale, "charts.title")}</h2>
      <Card>
        <CardHeader>
          <CardTitle>{tr(locale, "charts.card.speedAlt")}</CardTitle>
        </CardHeader>
        <CardContent>
          <TimelineChart
            id="speed-alt"
            x={x}
            scrubber={scrubber}
            onHover={setScrubber}
            lines={[
              { label: tr(locale, "charts.series.speed"), color: "#22d3ee", accessor: (i) => frames[i].speed ?? 0 },
              { label: tr(locale, "charts.series.altitude"), color: "#34d399", accessor: (i) => frames[i].alt ?? 0, yAxis: "right" }
            ]}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{tr(locale, "charts.card.accel")}</CardTitle>
        </CardHeader>
        <CardContent>
          <TimelineChart
            id="accel"
            x={x}
            scrubber={scrubber}
            onHover={setScrubber}
            lines={[
              { label: tr(locale, "charts.series.accelX"), color: "#38bdf8", accessor: (i) => frames[i].accelX ?? 0 },
              { label: tr(locale, "charts.series.accelY"), color: "#4ade80", accessor: (i) => frames[i].accelY ?? 0 },
              { label: tr(locale, "charts.series.accelZ"), color: "#f97316", accessor: (i) => frames[i].accelZ ?? 0 }
            ]}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Gyroscope (deg/s)</CardTitle>
        </CardHeader>
        <CardContent>
          <TimelineChart
            id="gyro"
            x={x}
            scrubber={scrubber}
            onHover={setScrubber}
            lines={[
              { label: "Gyro X", color: "#0ea5e9", accessor: (i) => finiteOrUndefined(frames[i].gyroX) },
              { label: "Gyro Y", color: "#22c55e", accessor: (i) => finiteOrUndefined(frames[i].gyroY) },
              { label: "Gyro Z", color: "#f97316", accessor: (i) => finiteOrUndefined(frames[i].gyroZ) }
            ]}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{tr(locale, "charts.card.attitude")}</CardTitle>
        </CardHeader>
        <CardContent>
          <TimelineChart
            id="attitude"
            x={x}
            scrubber={scrubber}
            onHover={setScrubber}
            lines={[
              { label: "Roll", color: "#2dd4bf", accessor: (i) => frames[i].roll ?? 0 },
              { label: "Pitch", color: "#60a5fa", accessor: (i) => frames[i].pitch ?? 0 },
              { label: "Yaw", color: "#f43f5e", accessor: (i) => frames[i].yaw ?? 0 }
            ]}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{tr(locale, "charts.card.vertical")}</CardTitle>
        </CardHeader>
        <CardContent>
          <TimelineChart
            id="vertical"
            x={x}
            scrubber={scrubber}
            onHover={setScrubber}
            lines={[
              { label: tr(locale, "charts.series.climbRate"), color: "#f59e0b", accessor: (i) => finiteOrUndefined(frames[i].climbRate) },
              { label: tr(locale, "charts.series.altitude"), color: "#22c55e", accessor: (i) => finiteOrUndefined(frames[i].alt), yAxis: "right" }
            ]}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{tr(locale, "charts.card.battery")}</CardTitle>
        </CardHeader>
        <CardContent>
          <TimelineChart
            id="battery"
            x={x}
            scrubber={scrubber}
            onHover={setScrubber}
            lines={[
              { label: tr(locale, "charts.series.battery"), color: "#eab308", accessor: (i) => finiteOrUndefined(frames[i].battery) }
            ]}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Barometer and Vibration</CardTitle>
        </CardHeader>
        <CardContent>
          <TimelineChart
            id="baro-vibe"
            x={x}
            scrubber={scrubber}
            onHover={setScrubber}
            lines={[
              { label: "Pressure (Pa)", color: "#f59e0b", accessor: (i) => finiteOrUndefined(frames[i].pressure) },
              { label: "Vibration", color: "#a78bfa", accessor: (i) => finiteOrUndefined(frames[i].vibration), yAxis: "right" }
            ]}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{tr(locale, "charts.card.accelNorm")}</CardTitle>
        </CardHeader>
        <CardContent>
          <TimelineChart
            id="accel-norm"
            x={x}
            scrubber={scrubber}
            onHover={setScrubber}
            lines={[
              {
                label: tr(locale, "charts.series.accelNorm"),
                color: "#a78bfa",
                accessor: (i) => {
                  const ax = finiteOrZero(frames[i].accelX);
                  const ay = finiteOrZero(frames[i].accelY);
                  const az = finiteOrZero(frames[i].accelZ);
                  if (!Number.isFinite(frames[i].accelX) && !Number.isFinite(frames[i].accelY) && !Number.isFinite(frames[i].accelZ)) {
                    return Number.NaN;
                  }
                  return Math.sqrt(ax * ax + ay * ay + az * az);
                }
              }
            ]}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Sensor vs Reference Error (timeline)</CardTitle>
        </CardHeader>
        <CardContent>
          <TimelineChart
            id="sensor-gps-error"
            x={x}
            scrubber={scrubber}
            onHover={setScrubber}
            lines={[
              { label: "Position Error (m)", color: "#ef4444", accessor: (i) => finiteOrNaN(frames[i].positionErrorM) }
            ]}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>INS vs Reference Trajectory (2D)</CardTitle>
        </CardHeader>
        <CardContent>
          <Trajectory2DChart ins={insTrajectory} reference={referenceTrajectory} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Sensor vs Reference Error (summary)</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-3 md:grid-cols-2">
            <div className="rounded-lg border border-border/70 p-3">
              <p className="text-xs text-muted-foreground">Endpoint Error</p>
              <p className="text-xl font-semibold">{(data.metrics.endpointErrorM ?? 0).toFixed(2)} m</p>
            </div>
            <div className="rounded-lg border border-border/70 p-3">
              <p className="text-xs text-muted-foreground">RMS Crosstrack</p>
              <p className="text-xl font-semibold">{(data.metrics.rmsCrosstrackErrorM ?? 0).toFixed(2)} m</p>
            </div>
            <div className="rounded-lg border border-border/70 p-3">
              <p className="text-xs text-muted-foreground">ATE</p>
              <p className="text-xl font-semibold">{(data.metrics.ateM ?? 0).toFixed(2)} m</p>
            </div>
            <div className="rounded-lg border border-border/70 p-3">
              <p className="text-xs text-muted-foreground">P95 Crosstrack</p>
              <p className="text-xl font-semibold">{(data.metrics.p95CrosstrackErrorM ?? 0).toFixed(2)} m</p>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
