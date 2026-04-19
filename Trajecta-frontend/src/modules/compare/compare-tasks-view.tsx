import { useEffect, useMemo, useState } from "react";
import { RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { compareTasks, downloadTrajectory, ApiClientError } from "@/lib/api";
import { t } from "@/lib/i18n";
import { parseTrajectory } from "@/hooks/useFlightData";
import { useFlightStore } from "@/store/flight-store";
import { useLocaleStore } from "@/store/locale-store";
import type { TaskComparisonInfo } from "@/types/flight";

type CompareSnapshot = {
  taskId: number;
  title: string;
  status: string;
  metrics: Record<string, number | null | undefined>;
  delta: Record<string, number | null | undefined>;
  params: Record<string, string | number | boolean | null>;
  frameCount: number;
  eventCount: number;
};

const MAX_COMPARE_TASKS = 6;

function toDisplay(value: unknown): string {
  if (value == null) {
    return "-";
  }
  if (typeof value === "number") {
    if (!Number.isFinite(value)) {
      return "-";
    }
    return Number.isInteger(value) ? String(value) : value.toFixed(4);
  }
  if (typeof value === "boolean") {
    return value ? "true" : "false";
  }
  if (typeof value === "string") {
    return value;
  }
  return String(value);
}

function sanitizeTaskIds(ids: number[]): number[] {
  const out: number[] = [];
  const seen = new Set<number>();
  for (const id of ids) {
    if (!Number.isFinite(id) || id <= 0 || seen.has(id)) {
      continue;
    }
    seen.add(id);
    out.push(Math.trunc(id));
    if (out.length >= MAX_COMPARE_TASKS) {
      break;
    }
  }
  return out;
}

export function CompareTasksView() {
  const locale = useLocaleStore((s) => s.locale);
  const auth = useFlightStore((s) => s.auth);
  const selectedTaskIds = useFlightStore((s) => s.selectedTaskIds);
  const setError = useFlightStore((s) => s.setError);

  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState("");
  const [changedOnly, setChangedOnly] = useState(true);
  const [baselineTaskId, setBaselineTaskId] = useState<number | null>(null);
  const [comparison, setComparison] = useState<TaskComparisonInfo | null>(null);
  const [snapshots, setSnapshots] = useState<CompareSnapshot[]>([]);

  const compareTaskIds = useMemo(() => sanitizeTaskIds(selectedTaskIds), [selectedTaskIds]);

  useEffect(() => {
    if (compareTaskIds.length === 0) {
      setBaselineTaskId(null);
      return;
    }
    if (!baselineTaskId || !compareTaskIds.includes(baselineTaskId)) {
      setBaselineTaskId(compareTaskIds[0]);
    }
  }, [baselineTaskId, compareTaskIds]);

  async function refresh() {
    if (!auth.token || compareTaskIds.length < 2 || !baselineTaskId) {
      setComparison(null);
      setSnapshots([]);
      return;
    }

    setLoading(true);
    setError(null, "tasks");

    try {
      const compared = await compareTasks({
        token: auth.token,
        taskIds: compareTaskIds,
        baselineTaskId
      });

      const parsed = await Promise.all(
        compared.items.map(async (item) => {
          if (item.status !== "COMPLETED") {
            return {
              taskId: item.taskId,
              title: item.title,
              status: item.status,
              metrics: item.metrics,
              delta: item.delta,
              params: {},
              frameCount: 0,
              eventCount: 0
            } satisfies CompareSnapshot;
          }

          try {
            const trajectory = await downloadTrajectory({ token: auth.token, taskId: item.taskId });
            const normalized = parseTrajectory(trajectory);
            return {
              taskId: item.taskId,
              title: item.title,
              status: item.status,
              metrics: item.metrics,
              delta: item.delta,
              params: normalized.params,
              frameCount: normalized.frames.length,
              eventCount: normalized.events.length
            } satisfies CompareSnapshot;
          } catch {
            return {
              taskId: item.taskId,
              title: item.title,
              status: item.status,
              metrics: item.metrics,
              delta: item.delta,
              params: {},
              frameCount: 0,
              eventCount: 0
            } satisfies CompareSnapshot;
          }
        })
      );

      setComparison(compared);
      setSnapshots(parsed);
    } catch (error) {
      setComparison(null);
      setSnapshots([]);
      setError(error instanceof ApiClientError ? error.message : "Failed to compare tasks", "tasks");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, [auth.token, baselineTaskId, compareTaskIds.join(",")]);

  const metricKeys = useMemo(() => {
    const keys = new Set<string>();
    for (const item of snapshots) {
      for (const key of Object.keys(item.delta)) {
        keys.add(key);
      }
    }
    return Array.from(keys).sort((a, b) => a.localeCompare(b));
  }, [snapshots]);

  const parameterRows = useMemo(() => {
    const allNames = new Set<string>();
    for (const item of snapshots) {
      for (const key of Object.keys(item.params)) {
        allNames.add(key);
      }
    }

    const q = query.trim().toLowerCase();

    return Array.from(allNames)
      .sort((a, b) => a.localeCompare(b))
      .map((name) => {
        const values = snapshots.map((item) => item.params[name]);
        const normalized = values.map((value) => toDisplay(value));
        const changed = new Set(normalized).size > 1;
        return {
          name,
          values,
          changed
        };
      })
      .filter((row) => (changedOnly ? row.changed : true))
      .filter((row) => (q ? row.name.toLowerCase().includes(q) : true));
  }, [changedOnly, query, snapshots]);

  if (compareTaskIds.length < 2) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>{t(locale, "compare.title")}</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          {t(locale, "compare.needSelection")}
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>{t(locale, "compare.title")}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="grid gap-2 md:grid-cols-[1fr_auto_auto]">
            <select
              className="h-10 rounded-md border border-white/10 bg-[rgba(7,8,10,0.82)] px-3 text-sm"
              value={baselineTaskId ?? ""}
              onChange={(event) => setBaselineTaskId(Number.parseInt(event.target.value, 10))}
            >
              {compareTaskIds.map((taskId) => (
                <option key={taskId} value={taskId}>
                  {t(locale, "compare.baselinePrefix")} #{taskId}
                </option>
              ))}
            </select>
            <Button variant="outline" onClick={() => setChangedOnly((v) => !v)}>
              {changedOnly ? t(locale, "compare.showAll") : t(locale, "compare.showChanged")}
            </Button>
            <Button onClick={() => void refresh()} disabled={loading}>
              <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
              {loading ? t(locale, "common.loading") : t(locale, "compare.refresh")}
            </Button>
          </div>
          <Input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={t(locale, "compare.searchParam")}
          />
          {selectedTaskIds.length > MAX_COMPARE_TASKS ? (
            <p className="text-xs text-muted-foreground">
              {t(locale, "compare.limitNotice", { limit: MAX_COMPARE_TASKS })}
            </p>
          ) : null}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t(locale, "compare.logsSection")}</CardTitle>
        </CardHeader>
        <CardContent className="overflow-auto">
          <table className="w-full min-w-[720px] text-sm">
            <thead className="bg-background/60 text-xs uppercase text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">{t(locale, "compare.task")}</th>
                <th className="px-3 py-2 text-left">{t(locale, "compare.status")}</th>
                <th className="px-3 py-2 text-left">{t(locale, "compare.frames")}</th>
                <th className="px-3 py-2 text-left">{t(locale, "compare.events")}</th>
                <th className="px-3 py-2 text-left">{t(locale, "compare.maxAltitude")}</th>
                <th className="px-3 py-2 text-left">{t(locale, "compare.maxSpeed")}</th>
                <th className="px-3 py-2 text-left">{t(locale, "compare.duration")}</th>
              </tr>
            </thead>
            <tbody>
              {snapshots.map((item) => (
                <tr key={item.taskId} className="border-t border-border/70">
                  <td className="px-3 py-2">#{item.taskId} {item.title}</td>
                  <td className="px-3 py-2">{item.status}</td>
                  <td className="px-3 py-2">{toDisplay(item.frameCount)}</td>
                  <td className="px-3 py-2">{toDisplay(item.eventCount)}</td>
                  <td className="px-3 py-2">{toDisplay(item.metrics.maxAltitude)}</td>
                  <td className="px-3 py-2">{toDisplay(item.metrics.maxSpeed)}</td>
                  <td className="px-3 py-2">{toDisplay(item.metrics.flightDurationSec)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t(locale, "compare.deltaSection", { baseline: comparison?.baselineTaskId ?? baselineTaskId ?? 0 })}</CardTitle>
        </CardHeader>
        <CardContent className="overflow-auto">
          <table className="w-full min-w-[720px] text-sm">
            <thead className="bg-background/60 text-xs uppercase text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">{t(locale, "compare.metric")}</th>
                {snapshots.map((item) => (
                  <th key={`delta-task-${item.taskId}`} className="px-3 py-2 text-left">#{item.taskId}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {metricKeys.map((metricName) => (
                <tr key={metricName} className="border-t border-border/70">
                  <td className="px-3 py-2 font-mono text-xs text-muted-foreground">{metricName}</td>
                  {snapshots.map((item) => (
                    <td key={`${metricName}-${item.taskId}`} className="px-3 py-2">{toDisplay(item.delta[metricName])}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t(locale, "compare.paramsSection")}</CardTitle>
        </CardHeader>
        <CardContent className="overflow-auto">
          <table className="w-full min-w-[960px] text-sm">
            <thead className="bg-background/60 text-xs uppercase text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left">{t(locale, "compare.param")}</th>
                {snapshots.map((item) => (
                  <th key={`param-task-${item.taskId}`} className="px-3 py-2 text-left">#{item.taskId}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {parameterRows.length === 0 ? (
                <tr>
                  <td className="px-3 py-3 text-muted-foreground" colSpan={snapshots.length + 1}>
                    {t(locale, "compare.noParams")}
                  </td>
                </tr>
              ) : (
                parameterRows.map((row) => (
                  <tr key={row.name} className={`border-t border-border/70 ${row.changed ? "bg-[hsl(var(--accent))]/5" : ""}`}>
                    <td className="px-3 py-2 font-mono text-xs text-muted-foreground">{row.name}</td>
                    {row.values.map((value, index) => (
                      <td key={`${row.name}-${snapshots[index]?.taskId ?? index}`} className="px-3 py-2">{toDisplay(value)}</td>
                    ))}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </CardContent>
      </Card>
    </div>
  );
}
