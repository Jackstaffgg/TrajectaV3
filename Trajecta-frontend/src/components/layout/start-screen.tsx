import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AlertTriangle, CheckCircle2, FileArchive, LoaderCircle, UploadCloud } from "lucide-react";
import { useFlightData } from "@/hooks/useFlightData";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { createTaskFolder, deleteTaskFolder, getTaskFolderById, getTaskFolderTree, updateTaskFolder } from "@/lib/api";
import { localizeTaskStatus, t } from "@/lib/i18n";
import { MAX_BIN_UPLOAD_MB, validateBinTelemetryFile } from "@/lib/upload";
import { useLocaleStore } from "@/store/locale-store";
import { useFlightStore } from "@/store/flight-store";
import type { TaskFolderInfo, TaskStatus } from "@/types/flight";

function statusProgress(status: TaskStatus): number {
  if (status === "PENDING") return 22;
  if (status === "PROCESSING") return 64;
  if (status === "COMPLETED") return 100;
  if (status === "FAILED" || status === "CANCELLED") return 100;
  return 0;
}

export function StartScreen() {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [drag, setDrag] = useState(false);
  const [title, setTitle] = useState("Flight analysis");
  const { loadFromBinBatch, rerunTaskWithParams } = useFlightData();
  const auth = useFlightStore((s) => s.auth);
  const currentTask = useFlightStore((s) => s.currentTask);
  const loading = useFlightStore((s) => s.loading);
  const setError = useFlightStore((s) => s.setError);
  const locale = useLocaleStore((s) => s.locale);
  const [folders, setFolders] = useState<TaskFolderInfo[]>([]);
  const [selectedFolderId, setSelectedFolderId] = useState<number | null>(null);
  const [folderInput, setFolderInput] = useState("");
  const [folderBusy, setFolderBusy] = useState(false);
  const [folderMessage, setFolderMessage] = useState<string | null>(null);
  const [queueState, setQueueState] = useState<{
    running: boolean;
    total: number;
    submitted: number;
    failed: number;
    currentFileName: string;
  }>({ running: false, total: 0, submitted: 0, failed: 0, currentFileName: "" });

  const refreshFolders = useCallback(async () => {
    if (!auth.token) {
      setFolders([]);
      return;
    }
    try {
      const data = await getTaskFolderTree({ token: auth.token });
      setFolders(data);
    } catch {
      setFolders([]);
    }
  }, [auth.token]);

  useEffect(() => {
    let active = true;
    if (!auth.token) {
      setFolders([]);
      return;
    }

    void refreshFolders().catch(() => {
      if (active) {
        setFolders([]);
      }
    });

    return () => {
      active = false;
    };
  }, [auth.token, refreshFolders]);

  useEffect(() => {
    if (!auth.token || !selectedFolderId) {
      return;
    }

    let active = true;
    void getTaskFolderById({ token: auth.token, folderId: selectedFolderId })
      .then((folder) => {
        if (active) {
          setFolderInput(folder.name);
        }
      })
      .catch(() => {
        // Ignore folder name prefill issues to keep upload flow responsive.
      });

    return () => {
      active = false;
    };
  }, [auth.token, selectedFolderId]);

  const folderOptions = useMemo(() => {
    const out: Array<{ id: number; label: string }> = [];
    const walk = (nodes: TaskFolderInfo[], depth: number) => {
      for (const node of nodes) {
        out.push({ id: node.id, label: `${"  ".repeat(depth)}${node.name}` });
        if (Array.isArray(node.children) && node.children.length > 0) {
          walk(node.children, depth + 1);
        }
      }
    };
    walk(folders, 0);
    return out;
  }, [folders]);

  async function handleCreateFolder() {
    if (!auth.token) {
      return;
    }
    const name = folderInput.trim();
    if (!name) {
      setFolderMessage(t(locale, "tasks.folderNameRequired"));
      return;
    }

    setFolderBusy(true);
    setFolderMessage(null);
    try {
      const created = await createTaskFolder({
        token: auth.token,
        name,
        parentId: selectedFolderId ?? undefined
      });
      await refreshFolders();
      setSelectedFolderId(created.id);
      setFolderInput("");
      setFolderMessage(t(locale, "tasks.folderCreateSuccess", { name: created.name }));
    } catch (error) {
      setFolderMessage(error instanceof Error ? error.message : t(locale, "tasks.folderCreateFailed"));
    } finally {
      setFolderBusy(false);
    }
  }

  async function handleRenameFolder() {
    if (!auth.token || !selectedFolderId) {
      return;
    }
    const name = folderInput.trim();
    if (!name) {
      setFolderMessage(t(locale, "tasks.folderRenameRequired"));
      return;
    }

    setFolderBusy(true);
    setFolderMessage(null);
    try {
      const updated = await updateTaskFolder({
        token: auth.token,
        folderId: selectedFolderId,
        name
      });
      await refreshFolders();
      setFolderInput("");
      setFolderMessage(t(locale, "tasks.folderRenameSuccess", { name: updated.name }));
    } catch (error) {
      setFolderMessage(error instanceof Error ? error.message : t(locale, "tasks.folderRenameFailed"));
    } finally {
      setFolderBusy(false);
    }
  }

  async function handleDeleteFolder() {
    if (!auth.token || !selectedFolderId) {
      return;
    }

    const accepted = window.confirm(t(locale, "tasks.folderDeleteConfirm"));
    if (!accepted) {
      return;
    }

    setFolderBusy(true);
    setFolderMessage(null);
    try {
      await deleteTaskFolder({ token: auth.token, folderId: selectedFolderId, recursive: true });
      await refreshFolders();
      setSelectedFolderId(null);
      setFolderMessage(t(locale, "tasks.folderDeleteSuccess"));
    } catch (error) {
      setFolderMessage(error instanceof Error ? error.message : t(locale, "tasks.folderDeleteFailed"));
    } finally {
      setFolderBusy(false);
    }
  }

  function safeText(value: unknown, fallback = "") {
    if (typeof value === "string") return value;
    if (typeof value === "number" || typeof value === "boolean") return String(value);
    return fallback;
  }

  function uniqueFiles(files: File[]) {
    const seen = new Set<string>();
    const out: File[] = [];
    for (const file of files) {
      const key = `${file.name}:${file.size}:${file.lastModified}`;
      if (seen.has(key)) {
        continue;
      }
      seen.add(key);
      out.push(file);
    }
    return out;
  }

  async function handleFiles(files: File[]) {
    const deduped = uniqueFiles(files);
    const wrongExtension = deduped.filter((file) => validateBinTelemetryFile(file) === "invalid_extension");
    const tooLarge = deduped.filter((file) => validateBinTelemetryFile(file) === "too_large");
    const valid = deduped.filter((file) => validateBinTelemetryFile(file) === "ok");

    if (valid.length === 0) {
      if (tooLarge.length > 0) {
        setError(t(locale, "tasks.maxSizeExceeded", { maxMb: MAX_BIN_UPLOAD_MB }), "tasks");
      } else {
        setError("Only .bin telemetry files are supported", "tasks");
      }
      return;
    }

    setError(null, "tasks");
    setQueueState({ running: true, total: valid.length, submitted: 0, failed: 0, currentFileName: "" });

    const result = await loadFromBinBatch(valid, title.trim(), selectedFolderId ?? undefined, (progress) => {
      setQueueState({
        running: true,
        total: progress.total,
        submitted: progress.submitted,
        failed: progress.failed,
        currentFileName: progress.currentFileName
      });
    });

    setQueueState({
      running: false,
      total: result.total,
      submitted: result.submitted,
      failed: result.failed.length,
      currentFileName: ""
    });

    const skipMessages: string[] = [];
    if (wrongExtension.length > 0) {
      skipMessages.push(`Skipped ${wrongExtension.length} non-bin file(s)`);
    }
    if (tooLarge.length > 0) {
      skipMessages.push(`Skipped ${tooLarge.length} oversized file(s) (>${MAX_BIN_UPLOAD_MB}MB)`);
    }
    if (skipMessages.length > 0) {
      setError(skipMessages.join(". "), "tasks");
    }
  }

  async function handleReanalyzeCurrent() {
    if (!currentTask?.id) {
      return;
    }

    const titleInput = window.prompt("New task title", `${safeText(currentTask.title, "Flight analysis")} (reanalyze)`);
    if (titleInput === null) {
      return;
    }

    const dtInput = window.prompt("timelineDtSeconds (optional, > 0)", "0.1");
    if (dtInput === null) {
      return;
    }

    const rawDt = dtInput.trim();
    let timelineDtSeconds: number | undefined;
    if (rawDt) {
      const parsed = Number(rawDt);
      if (!Number.isFinite(parsed) || parsed <= 0) {
        setError("timelineDtSeconds must be a positive number", "tasks");
        return;
      }
      timelineDtSeconds = parsed;
    }

    await rerunTaskWithParams({
      taskId: currentTask.id,
      title: titleInput.trim() || undefined,
      folderId: selectedFolderId ?? undefined,
      timelineDtSeconds
    });
  }

  return (
    <div className="flex min-h-[60vh] items-center justify-center p-4">
      <Card className="w-full max-w-4xl border-white/10 bg-card/95">
        <CardHeader className="space-y-2">
          <p className="inline-flex w-fit items-center rounded-full border border-white/15 bg-[hsl(var(--accent))]/20 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-foreground">
            Mission Upload
          </p>
          <CardTitle className="text-2xl">{t(locale, "tasks.startTitle")}</CardTitle>
          <p className="text-sm leading-relaxed text-muted-foreground">
            {t(locale, "tasks.startSubtitle")}
          </p>
        </CardHeader>
        <CardContent>
          <div className="mb-4 space-y-2">
            <p className="text-xs uppercase tracking-wide text-muted-foreground">{t(locale, "tasks.taskTitle")}</p>
            <Input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Flight #102 telemetry"
            />
          </div>
          <div className="mb-4 space-y-2">
            <p className="text-xs uppercase tracking-wide text-muted-foreground">{t(locale, "tasks.folderLabel")}</p>
            <select
              className="h-10 w-full rounded-md border border-white/10 bg-[rgba(7,8,10,0.82)] px-3 text-sm"
              value={selectedFolderId ?? ""}
              onChange={(e) => {
                const value = e.target.value;
                setSelectedFolderId(value ? Number(value) : null);
              }}
            >
              <option value="">{t(locale, "tasks.folderRoot")}</option>
              {folderOptions.map((folder) => (
                <option key={folder.id} value={folder.id}>
                  {folder.label}
                </option>
              ))}
            </select>
            <div className="grid gap-2 md:grid-cols-[1fr_auto_auto_auto]">
              <Input
                value={folderInput}
                onChange={(e) => setFolderInput(e.target.value)}
                placeholder={t(locale, "tasks.folderNamePlaceholder")}
                disabled={folderBusy}
              />
              <button
                type="button"
                className="rounded-md border border-white/10 bg-[hsl(var(--accent))]/85 px-3 py-2 text-xs font-semibold text-white transition-[opacity,box-shadow] hover:opacity-95 hover:shadow-[inset_0_1px_0_0_rgba(255,255,255,0.18),0_0_0_1px_rgba(255,255,255,0.2),0_0_16px_hsla(0,100%,69%,0.35),0_10px_22px_rgba(0,0,0,0.36)] disabled:opacity-50"
                onClick={() => void handleCreateFolder()}
                disabled={folderBusy || !folderInput.trim()}
              >
                {t(locale, "tasks.folderCreate")}
              </button>
              <button
                type="button"
                className="rounded-md border border-border/70 px-3 py-2 text-xs font-semibold text-foreground transition hover:bg-muted/35 disabled:opacity-50"
                onClick={() => void handleRenameFolder()}
                disabled={folderBusy || !selectedFolderId || !folderInput.trim()}
              >
                {t(locale, "tasks.folderRename")}
              </button>
              <button
                type="button"
                className="rounded-md border border-red-300/30 bg-red-500/15 px-3 py-2 text-xs font-semibold text-red-200 transition hover:bg-red-500/25 disabled:opacity-50"
                onClick={() => void handleDeleteFolder()}
                disabled={folderBusy || !selectedFolderId}
              >
                {t(locale, "tasks.folderDelete")}
              </button>
            </div>
            {folderMessage ? <p className="text-xs text-muted-foreground">{folderMessage}</p> : null}
          </div>
          <button
            className={`group w-full rounded-2xl border-2 border-dashed p-10 text-center transition ${
              drag
                ? "border-[hsl(var(--accent))]/70 bg-[hsl(var(--accent))]/15"
                : "border-white/12 bg-[rgba(7,8,10,0.65)] hover:border-[hsl(var(--ring))]/45 hover:bg-[rgba(10,12,15,0.9)]"
            }`}
            onClick={() => inputRef.current?.click()}
            onDragOver={(e) => {
              e.preventDefault();
              setDrag(true);
            }}
            onDragLeave={() => setDrag(false)}
            onDrop={(e) => {
              e.preventDefault();
              setDrag(false);
              const files = Array.from(e.dataTransfer.files ?? []);
              if (files.length > 0) {
                void handleFiles(files);
              }
            }}
          >
            <UploadCloud className="mx-auto mb-2 h-8 w-8 text-foreground transition group-hover:scale-105" />
            <p className="text-base font-semibold">{t(locale, "tasks.dragTitle")}</p>
            <p className="mt-1 text-xs text-muted-foreground">{t(locale, "tasks.dragHintMulti")}</p>
            <p className="mt-1 text-[11px] text-muted-foreground">{t(locale, "tasks.maxSizeExceeded", { maxMb: MAX_BIN_UPLOAD_MB })}</p>
            <div className="mt-4 inline-flex items-center gap-2 rounded-md border border-white/10 bg-[#1b1c1e] px-3 py-1 text-xs text-muted-foreground">
              <FileArchive className="h-3.5 w-3.5" />
              .bin
            </div>
          </button>

          {currentTask ? (
            <div className="animate-rise mt-4 rounded-xl border border-border/80 bg-background/35 p-4">
              <div className="mb-2 flex items-center justify-between text-xs">
                <p className="font-medium text-foreground">Task #{currentTask.id}</p>
                <p className="rounded-full border border-border/70 px-2 py-0.5 text-[10px] text-muted-foreground">{localizeTaskStatus(currentTask.status, locale)}</p>
              </div>
              <div className="progress-track">
                <div
                  className={`progress-bar ${currentTask.status === "FAILED" || currentTask.status === "CANCELLED" ? "progress-bar-danger" : ""}`}
                  style={{ width: `${statusProgress(currentTask.status)}%` }}
                />
              </div>
              <div className="mt-2 flex items-center gap-2 text-xs">
                {currentTask.status === "COMPLETED" ? (
                  <>
                    <CheckCircle2 className="h-4 w-4 text-emerald-300" />
                    <span className="text-emerald-200">{t(locale, "tasks.completedHint")}</span>
                  </>
                ) : currentTask.status === "FAILED" || currentTask.status === "CANCELLED" ? (
                  <>
                    <AlertTriangle className="h-4 w-4 text-rose-300" />
                    <span className="text-rose-200">{safeText(currentTask.errorMessage, "Task failed")}</span>
                  </>
                ) : (
                  <>
                    <LoaderCircle className="h-4 w-4 animate-spin text-[hsl(var(--accent))]" />
                    <span className="text-foreground">{t(locale, "tasks.processingHint")}</span>
                  </>
                )}
              </div>
              <div className="mt-3 flex justify-end">
                <button
                  type="button"
                  className="rounded-lg border border-border/70 px-3 py-1 text-xs text-foreground hover:bg-muted/40 disabled:opacity-50"
                  onClick={() => void handleReanalyzeCurrent()}
                  disabled={loading}
                >
                  {t(locale, "tasks.reanalyze")}
                </button>
              </div>
            </div>
          ) : null}

          {loading ? (
            <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-muted/80">
              <div className="h-full w-1/3 rounded-full bg-[hsl(var(--accent))] animate-shimmer" />
            </div>
          ) : null}

          {(queueState.running || queueState.total > 0) ? (
            <div className="mt-3 rounded-lg border border-border/70 bg-background/30 p-3 text-xs text-muted-foreground">
              <p>
                {t(locale, "tasks.batchProgress", {
                  submitted: queueState.submitted,
                  total: queueState.total,
                  failed: queueState.failed
                })}
              </p>
              {queueState.running && queueState.currentFileName ? (
                <p className="mt-1 text-[11px]">{t(locale, "tasks.batchCurrent", { file: queueState.currentFileName })}</p>
              ) : null}
            </div>
          ) : null}

          <input
            ref={inputRef}
            type="file"
            accept=".bin,application/octet-stream"
            multiple
            className="hidden"
            onChange={(e) => {
              const files = Array.from(e.target.files ?? []);
              if (files.length > 0) {
                void handleFiles(files);
              }
              e.currentTarget.value = "";
            }}
          />
        </CardContent>
      </Card>
    </div>
  );
}
