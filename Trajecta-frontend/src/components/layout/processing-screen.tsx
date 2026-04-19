import { AlertTriangle, CheckCircle2, LoaderCircle } from "lucide-react";
import { localizeTaskStatus, t } from "@/lib/i18n";
import { useLocaleStore } from "@/store/locale-store";
import { useFlightStore } from "@/store/flight-store";
import type { TaskStatus } from "@/types/flight";

function statusProgress(status: TaskStatus): number {
  if (status === "PENDING") return 20;
  if (status === "PROCESSING") return 70;
  if (status === "COMPLETED") return 100;
  if (status === "FAILED" || status === "CANCELLED") return 100;
  return 10;
}

export function ProcessingScreen() {
  const task = useFlightStore((s) => s.currentTask);
  const locale = useLocaleStore((s) => s.locale);

  const status = task?.status ?? "PENDING";
  const isDone = status === "COMPLETED";
  const isError = status === "FAILED" || status === "CANCELLED";

  return (
    <div className="flex min-h-[60vh] items-center justify-center">
      <div className="w-full max-w-lg rounded-xl border border-border/80 bg-card/95 px-8 py-6 text-center shadow-glow animate-rise">
        {isDone ? (
          <CheckCircle2 className="mx-auto mb-3 h-8 w-8 text-emerald-300" />
        ) : isError ? (
          <AlertTriangle className="mx-auto mb-3 h-8 w-8 text-rose-300" />
        ) : (
          <LoaderCircle className="mx-auto mb-3 h-8 w-8 animate-spin text-[hsl(var(--accent))]" />
        )}

        <p className="text-sm font-medium">
          {isDone ? t(locale, "tasks.ready") : isError ? t(locale, "tasks.errorDone") : t(locale, "tasks.processing")}
        </p>
        <p className="mt-1 text-xs text-muted-foreground">{t(locale, "tasks.pipeline")}</p>

        <div className="mt-4 progress-track">
          <div className={`progress-bar ${isError ? "progress-bar-danger" : ""}`} style={{ width: `${statusProgress(status)}%` }} />
        </div>

        {task ? (
          <p className="mt-3 text-xs text-foreground">
            Task #{task.id}: {localizeTaskStatus(task.status, locale)}
            {task.errorMessage ? ` - ${task.errorMessage}` : ""}
          </p>
        ) : null}
      </div>
    </div>
  );
}
