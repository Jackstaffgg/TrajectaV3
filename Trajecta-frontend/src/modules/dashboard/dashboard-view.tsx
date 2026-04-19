import { useEffect, useMemo, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { t } from "@/lib/i18n";
import { useLocaleStore } from "@/store/locale-store";
import { useFlightStore } from "@/store/flight-store";
import { formatDuration, metersToKm } from "@/lib/utils";
import { compareTasks } from "@/lib/api";
import type { TaskComparisonInfo } from "@/types/flight";

function StatCard({ label, value, unit }: { label: string; value: string | number; unit?: string }) {
  return (
    <Card>
      <CardContent className="p-4">
        <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
        <p className="mt-2 text-3xl font-bold leading-none text-foreground">
          {value}
          {unit ? <span className="ml-1 text-sm text-muted-foreground">{unit}</span> : null}
        </p>
      </CardContent>
    </Card>
  );
}

export function DashboardView() {
  const data = useFlightStore((s) => s.data);
  const auth = useFlightStore((s) => s.auth);
  const currentTask = useFlightStore((s) => s.currentTask);
  const locale = useLocaleStore((s) => s.locale);
  const [comparison, setComparison] = useState<TaskComparisonInfo | null>(null);

  useEffect(() => {
    let active = true;
    const taskId = currentTask?.id;
    const sourceTaskId = currentTask?.sourceTaskId;
    if (!auth.token || !taskId || !sourceTaskId) {
      setComparison(null);
      return;
    }

    void compareTasks({ token: auth.token, taskIds: [sourceTaskId, taskId], baselineTaskId: sourceTaskId })
      .then((payload) => {
        if (active) {
          setComparison(payload);
        }
      })
      .catch(() => {
        if (active) {
          setComparison(null);
        }
      });

    return () => {
      active = false;
    };
  }, [auth.token, currentTask?.id, currentTask?.sourceTaskId]);

  const currentComparisonItem = useMemo(() => {
    if (!comparison || !currentTask?.id) {
      return null;
    }
    return comparison.items.find((item) => item.taskId === currentTask.id) ?? null;
  }, [comparison, currentTask?.id]);

  if (!data) {
    return null;
  }

  const m = data.metrics;
  const meta = data.metadata;
  const stats: Array<{ label: string; value: string | number; unit?: string }> = [
    { label: "Max Altitude", value: (m.maxAltitude ?? 0).toFixed(1), unit: "m" },
    { label: "Max Horizontal Speed", value: (m.maxSpeed ?? 0).toFixed(1), unit: "m/s" },
    { label: "Flight Duration", value: formatDuration(m.flightDurationSec ?? 0) },
    {
      label: "Horizontal Distance",
      value: metersToKm(m.horizontalDistanceMeters ?? m.totalDistanceMeters ?? 0),
      unit: "km"
    },
    {
      label: "3D Distance",
      value: metersToKm(m.totalDistance3dMeters ?? m.totalDistanceMeters ?? 0),
      unit: "km"
    },
    { label: "Max Vertical Speed", value: (m.maxVerticalSpeed ?? 0).toFixed(1), unit: "m/s" },
    {
      label: "IMU Sampling",
      value: m.imuRateHz ? m.imuRateHz.toFixed(1) : "-",
      unit: "Hz"
    },
    {
      label: "IMU Period",
      value: m.imuSamplingPeriodMs ? m.imuSamplingPeriodMs.toFixed(2) : "-",
      unit: "ms"
    },
    { label: "Used IMU Sensors", value: Math.round(m.usedImuCount ?? 0) },
    { label: "Used Barometers", value: Math.round(m.usedBaroCount ?? 0) },
    { label: "Peak |Accel|", value: (m.peakAbsAccelM ?? 0).toFixed(3), unit: "m/s^2" },
    { label: "Peak |Gyro|", value: (m.peakAbsGyroRadSec ?? 0).toFixed(3), unit: "rad/s" },
    { label: "Max Vibration", value: (m.maxVibration ?? 0).toFixed(3) },
    { label: "Trajectory Points", value: Math.round(m.pointCount ?? 0) }
  ];

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-2xl font-semibold">{t(locale, "dashboard.title")}</h2>
        <p className="text-sm text-muted-foreground">{t(locale, "dashboard.subtitle")}</p>
      </div>
      <div className="grid gap-4 md:grid-cols-3">
        {stats.map((stat) => (
          <StatCard key={stat.label} label={stat.label} value={stat.value} unit={stat.unit} />
        ))}
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Metadata</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-3 text-sm text-muted-foreground md:grid-cols-2">
          <p>Log: <span className="text-foreground">{String(meta.logName ?? "Unknown")}</span></p>
          <p>Vehicle: <span className="text-foreground">{String(meta.vehicleType ?? "Unknown")}</span></p>
          <p>GPS Units: <span className="text-foreground">{String(meta.gpsUnits ?? "WGS84")}</span></p>
          <p>Parser: <span className="text-foreground">{String(meta.parserVersion ?? "N/A")}</span></p>
        </CardContent>
      </Card>
      {currentComparisonItem ? (
        <Card>
          <CardHeader>
            <CardTitle>Comparison vs source task #{comparison?.baselineTaskId}</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-2 text-sm">
            {Object.entries(currentComparisonItem.delta).map(([metric, value]) => (
              <div key={metric} className="flex items-center justify-between border-b border-border/40 pb-1">
                <span className="text-muted-foreground">{metric}</span>
                <span className="font-medium text-foreground">{typeof value === "number" ? value.toFixed(3) : "-"}</span>
              </div>
            ))}
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}
