import { useEffect, useMemo, useRef } from "react";
import { FastForward, Pause, Play, Rewind, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { useFlightStore } from "@/store/flight-store";
import { interpolateFrame } from "@/modules/replay/replay-utils";
import { CartesianTraceView } from "@/modules/replay/cartesian-trace-view";

function formatReplayTime(sec: number) {
  const s = Math.max(0, Math.floor(sec));
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${String(m).padStart(2, "0")}:${String(r).padStart(2, "0")}`;
}

export function FlightReplayView() {
  const data = useFlightStore((s) => s.data);
  const replay = useFlightStore((s) => s.replay);
  const setTime = useFlightStore((s) => s.setReplayTime);
  const setPlaying = useFlightStore((s) => s.setReplayPlaying);
  const setSpeed = useFlightStore((s) => s.setReplaySpeed);

  const isPlayingRef = useRef(replay.isPlaying);
  const timeRef = useRef(replay.timeSec);
  const maxTimeRef = useRef(0);

  const frames = useMemo(
    () =>
      (data?.frames ?? [])
        .filter(
          (f) =>
            Number.isFinite(f.t) &&
            Number.isFinite(f.lat) &&
            Number.isFinite(f.lon) &&
            Math.abs(f.lat) <= 90 &&
            Math.abs(f.lon) <= 180
        )
        .sort((a, b) => a.t - b.t),
    [data?.frames]
  );

  const maxTime = frames.length ? frames[frames.length - 1].t : 0;
  const currentFrame = useMemo(() => interpolateFrame(frames, replay.timeSec), [frames, replay.timeSec]);
  const referenceFrames = useMemo(
    () =>
      frames
        .filter(
          (f) =>
            Number.isFinite(f.gpsLat) &&
            Number.isFinite(f.gpsLon) &&
            Math.abs(f.gpsLat as number) <= 90 &&
            Math.abs(f.gpsLon as number) <= 180
        )
        .map((f) => ({
          ...f,
          lat: f.gpsLat as number,
          lon: f.gpsLon as number,
          alt: Number.isFinite(f.gpsAlt) ? (f.gpsAlt as number) : f.alt,
        })),
    [frames]
  );
  const currentReferenceFrame = useMemo(
    () => (referenceFrames.length > 1 ? interpolateFrame(referenceFrames, replay.timeSec) : undefined),
    [referenceFrames, replay.timeSec]
  );
  const speedPresets = [0.25, 0.5, 1, 2, 4];

  useEffect(() => {
    if (replay.timeSec > maxTime && maxTime > 0) {
      setTime(maxTime);
      setPlaying(false);
    }
  }, [maxTime, replay.timeSec, setPlaying, setTime]);

  useEffect(() => {
    if (!replay.isPlaying || maxTime <= 0) {
      return;
    }

    let id = 0;
    let prev = performance.now();
    const loop = (now: number) => {
      const dt = (now - prev) / 1000;
      prev = now;
      const next = replay.timeSec + dt * replay.speed;
      if (next >= maxTime) {
        setTime(maxTime);
        setPlaying(false);
        return;
      }
      setTime(next);
      id = requestAnimationFrame(loop);
    };

    id = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(id);
  }, [replay.isPlaying, replay.speed, replay.timeSec, setPlaying, setTime, maxTime]);

  useEffect(() => {
    isPlayingRef.current = replay.isPlaying;
    timeRef.current = replay.timeSec;
    maxTimeRef.current = maxTime;
  }, [replay.isPlaying, replay.timeSec, maxTime]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (target && ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName)) {
        return;
      }

      if (event.code === "Space") {
        event.preventDefault();
        setPlaying(!isPlayingRef.current);
        return;
      }

      if (event.key === "ArrowLeft") {
        event.preventDefault();
        const next = Math.max(0, timeRef.current + (event.shiftKey ? -10 : -2));
        setTime(next);
        return;
      }

      if (event.key === "ArrowRight") {
        event.preventDefault();
        const next = Math.min(maxTimeRef.current, timeRef.current + (event.shiftKey ? 10 : 2));
        setTime(next);
        return;
      }

      if (event.key.toLowerCase() === "r") {
        setTime(0);
        setPlaying(false);
      }
    };

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [setPlaying, setTime]);

  if (!frames.length) {
    return <p className="text-sm text-muted-foreground">No frames found for replay.</p>;
  }

  return (
    <div className="space-y-3">
      <div className="relative h-[62vh] overflow-hidden rounded-xl border border-border bg-[rgba(7,8,10,0.95)]">
        <CartesianTraceView
          frames={frames}
          currentFrame={currentFrame}
          referenceFrames={referenceFrames}
          currentReferenceFrame={currentReferenceFrame}
        />
      </div>

      <div className="space-y-3 rounded-xl border border-border bg-card/80 p-3">
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => setTime(Math.max(0, replay.timeSec - 5))}>
            <Rewind className="h-4 w-4" />
            -5s
          </Button>
          <Button size="sm" onClick={() => setPlaying(!replay.isPlaying)}>
            {replay.isPlaying ? <Pause className="h-4 w-4" /> : <Play className="h-4 w-4" />}
            {replay.isPlaying ? "Pause" : "Play"}
          </Button>
          <Button variant="outline" size="sm" onClick={() => setTime(Math.min(maxTime, replay.timeSec + 5))}>
            <FastForward className="h-4 w-4" />
            +5s
          </Button>
          <Button variant="outline" size="sm" onClick={() => { setTime(0); setPlaying(false); }}>
            <RotateCcw className="h-4 w-4" />
            Reset
          </Button>
          {speedPresets.map((preset) => (
            <Button
              key={preset}
              variant={replay.speed === preset ? "default" : "outline"}
              size="sm"
              onClick={() => setSpeed(preset)}
            >
              {preset}x
            </Button>
          ))}
          <Badge>{formatReplayTime(replay.timeSec)} / {formatReplayTime(maxTime)}</Badge>
          <span className="text-xs text-muted-foreground">Keyboard: Space, ←/→, Shift+←/→, R</span>
        </div>

        <input
          className="h-1.5 w-full cursor-pointer appearance-none rounded bg-muted"
          type="range"
          min={0}
          max={maxTime}
          step={0.01}
          value={replay.timeSec}
          onChange={(e) => setTime(Number(e.target.value))}
        />
      </div>
    </div>
  );
}
