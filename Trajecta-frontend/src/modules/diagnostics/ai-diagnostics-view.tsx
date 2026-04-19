import { useMemo, useState } from "react";
import { RefreshCcw, Sparkles } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { localizeErrorMessage, t } from "@/lib/i18n";
import { useLocaleStore } from "@/store/locale-store";
import { useFlightStore } from "@/store/flight-store";
import { useFlightData } from "@/hooks/useFlightData";

export function AiDiagnosticsView() {
  const [loadingAppend, setLoadingAppend] = useState(false);
  const [loadingRegenerate, setLoadingRegenerate] = useState(false);
  const data = useFlightStore((s) => s.data);
  const currentTask = useFlightStore((s) => s.currentTask);
  const error = useFlightStore((s) => s.error);
  const locale = useLocaleStore((s) => s.locale);
  const { requestAiConclusion } = useFlightData();

  const aiConclusion = useMemo(
    () => data?.aiConclusion ?? currentTask?.aiConclusion ?? undefined,
    [data?.aiConclusion, currentTask?.aiConclusion]
  );

  const aiModel = useMemo(
    () => data?.aiModel ?? currentTask?.aiModel ?? undefined,
    [data?.aiModel, currentTask?.aiModel]
  );

  function safeText(value: unknown, fallback = "") {
    if (typeof value === "string") return value;
    if (typeof value === "number" || typeof value === "boolean") return String(value);
    return fallback;
  }

  async function requestAnalysis(force: boolean) {
    if (!data || !currentTask) {
      return;
    }

    if (force) {
      setLoadingRegenerate(true);
    } else {
      setLoadingAppend(true);
    }
    try {
      await requestAiConclusion(force);
    } finally {
      if (force) {
        setLoadingRegenerate(false);
      } else {
        setLoadingAppend(false);
      }
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t(locale, "diag.title")}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-muted-foreground">
          {t(locale, "diag.subtitle")}
        </p>
        {currentTask ? <p className="text-xs text-muted-foreground">{t(locale, "diag.task")} #{currentTask.id}</p> : null}
        {error ? <p className="text-xs text-rose-300">{localizeErrorMessage(error, locale)}</p> : null}
        <div className="flex flex-wrap gap-2">
          <Button
            onClick={() => void requestAnalysis(false)}
            disabled={loadingAppend || loadingRegenerate || !currentTask || !data}
          >
            <Sparkles className="h-4 w-4" />
            {loadingAppend
              ? t(locale, "diag.requestInProgress")
              : aiConclusion
              ? t(locale, "diag.refresh")
              : t(locale, "diag.generate")}
          </Button>
          <Button
            variant="outline"
            onClick={() => void requestAnalysis(true)}
            disabled={loadingAppend || loadingRegenerate || !currentTask || !data}
          >
            <RefreshCcw className="h-4 w-4" />
            {loadingRegenerate ? t(locale, "diag.regenerating") : t(locale, "diag.forceRegenerate")}
          </Button>
        </div>

        {aiModel ? (
          <p className="text-xs text-muted-foreground">
            {t(locale, "diag.model")}: <span className="font-medium text-foreground">{safeText(aiModel, "UNKNOWN")}</span>
          </p>
        ) : null}

        {aiConclusion ? (
          <div className="rounded-lg border border-border bg-background/50 p-4">
            <p className="mb-1 text-xs uppercase text-muted-foreground">{t(locale, "diag.conclusion")}</p>
            <p className="text-sm text-foreground">{safeText(aiConclusion, t(locale, "diag.noConclusion"))}</p>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
