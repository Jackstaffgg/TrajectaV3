import { useEffect, useMemo, useState, type ComponentType } from "react";
import {
  Activity,
  Bell,
  ChevronDown,
  ChevronUp,
  Radio,
  RefreshCw,
  Shield,
  UserCog
} from "lucide-react";
import { useFlightStore } from "@/store/flight-store";
import { useLocaleStore } from "@/store/locale-store";
import { localizeTaskStatus, t } from "@/lib/i18n";
import type { AnalysisMode, TaskInfo } from "@/types/flight";
import { cn } from "@/lib/utils";

type NavItem = {
  mode: AnalysisMode;
  labelKey: string;
  icon: ComponentType<{ className?: string }>;
  requiresTask?: boolean;
  requiresSelectedTasks?: number;
  adminOnly?: boolean;
};

type NavSection = {
  titleKey: string;
  items: NavItem[];
};

const sections: NavSection[] = [
  {
    titleKey: "sidebar.section.workspace",
    items: [{ mode: "tasks", labelKey: "sidebar.item.tasks", icon: Radio }]
  },
  {
    titleKey: "sidebar.section.analytics",
    items: [
      { mode: "analytics", labelKey: "sidebar.item.analyticsWorkspace", icon: Activity, requiresTask: true },
      { mode: "compare", labelKey: "sidebar.item.compare", icon: Activity, requiresSelectedTasks: 2 }
    ]
  },
  {
    titleKey: "sidebar.section.account",
    items: [
      { mode: "profile", labelKey: "sidebar.item.profile", icon: UserCog },
      { mode: "admin-users", labelKey: "sidebar.item.adminUsers", icon: Shield, adminOnly: true },
      { mode: "admin-notifications", labelKey: "sidebar.item.adminNotifications", icon: Bell, adminOnly: true }
    ]
  }
];

function normalizeRole(role: string | null | undefined): string {
  if (!role) {
    return "";
  }
  return role.toUpperCase().replace(/^ROLE_/, "");
}

type SidebarProps = {
  tasks: TaskInfo[];
  activeTaskId: number | null;
  folderOptions: Array<{ id: number; label: string }>;
  loadingTasks?: boolean;
  deletingTasks?: boolean;
  onTaskSelect: (task: TaskInfo) => void;
  onDeleteTasks: (taskIds: number[]) => void;
  onMoveTasks: (taskIds: number[], folderId: number | null) => void;
};

function taskStatusClass(status: TaskInfo["status"]) {
  if (status === "COMPLETED") return "text-emerald-300";
  if (status === "FAILED" || status === "CANCELLED") return "text-rose-300";
  if (status === "PROCESSING") return "text-amber-300";
  return "text-muted-foreground";
}

export function Sidebar({ tasks, activeTaskId, folderOptions, loadingTasks = false, deletingTasks = false, onTaskSelect, onDeleteTasks, onMoveTasks }: SidebarProps) {
  const mode = useFlightStore((s) => s.mode);
  const setMode = useFlightStore((s) => s.setMode);
  const auth = useFlightStore((s) => s.auth);
  const selectedTaskIds = useFlightStore((s) => s.selectedTaskIds);
  const setSelectedTaskIds = useFlightStore((s) => s.setSelectedTaskIds);
  const locale = useLocaleStore((s) => s.locale);

  const hasSelectedTask = Boolean(activeTaskId);
  const role = normalizeRole(auth.user?.role);
  const canAccessAdmin = role === "ADMIN" || role === "OWNER";
  const [tasksExpanded, setTasksExpanded] = useState(false);
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);
  const [moveTargetFolderId, setMoveTargetFolderId] = useState<string>("");

  const allTaskIds = useMemo(() => tasks.map((task) => task.id), [tasks]);
  const allSelected = allTaskIds.length > 0 && allTaskIds.every((id) => selectedTaskIds.includes(id));

  useEffect(() => {
    const availableIds = new Set(allTaskIds);
    const filtered = selectedTaskIds.filter((id) => availableIds.has(id));
    const changed =
      filtered.length !== selectedTaskIds.length ||
      filtered.some((id, idx) => id !== selectedTaskIds[idx]);

    if (changed) {
      setSelectedTaskIds(filtered);
    }
  }, [allTaskIds, selectedTaskIds, setSelectedTaskIds]);

  function toggleTaskSelection(taskId: number) {
    if (selectedTaskIds.includes(taskId)) {
      setSelectedTaskIds(selectedTaskIds.filter((id) => id !== taskId));
      return;
    }
    setSelectedTaskIds([...selectedTaskIds, taskId]);
  }

  function deleteSelected() {
    if (selectedTaskIds.length === 0) {
      return;
    }
    onDeleteTasks(selectedTaskIds);
    setSelectedTaskIds([]);
    setConfirmDeleteOpen(false);
  }

  function toggleSelectAll() {
    if (allSelected) {
      setSelectedTaskIds([]);
      return;
    }
    setSelectedTaskIds(allTaskIds);
  }

  function moveSelectedToRoot() {
    if (selectedTaskIds.length === 0) {
      return;
    }
    onMoveTasks(selectedTaskIds, null);
    setSelectedTaskIds([]);
    setMoveTargetFolderId("");
  }

  function moveSelectedToFolder() {
    if (selectedTaskIds.length === 0) {
      return;
    }
    const parsed = Number.parseInt(moveTargetFolderId, 10);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      return;
    }
    onMoveTasks(selectedTaskIds, parsed);
    setSelectedTaskIds([]);
    setMoveTargetFolderId("");
  }

  return (
    <aside className="panel-grid w-full border-b border-border/70 p-3 backdrop-blur md:sticky md:left-0 md:top-0 md:h-screen md:w-80 md:border-b-0 md:border-r">
      <div className="surface-panel h-full rounded-2xl p-3">
        <div className="mb-4 flex items-center justify-between md:block">
          <h1 className="text-lg font-bold tracking-wider text-foreground">TRAJECTA</h1>
        </div>

        <div className="space-y-3">
          {sections.map((section, sectionIndex) => {
            const visibleItems = section.items.filter((item) => {
              if (item.adminOnly && !canAccessAdmin) {
                return false;
              }
              if (item.requiresTask && !hasSelectedTask) {
                return false;
              }
              if (item.requiresSelectedTasks && selectedTaskIds.length < item.requiresSelectedTasks) {
                return false;
              }
              return true;
            });

            if (visibleItems.length === 0) {
              return null;
            }

            return (
              <section key={section.titleKey} className={cn(sectionIndex > 0 ? "surface-divider pt-3" : "")}> 
                <p className="mb-2 px-1 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                  {t(locale, section.titleKey)}
                </p>
                <nav className="grid grid-cols-2 gap-2 md:grid-cols-1">
                  {visibleItems.map((item, idx) => {
                    const Icon = item.icon;
                    const active = mode === item.mode;
                    return (
                      <button
                        key={item.mode}
                        className={cn(
                           "sidebar-menu-item nav-item animate-rise flex items-center gap-2 rounded-xl px-3 py-2 text-left text-sm transition",
                          active
                            ? "sidebar-menu-item-active text-foreground"
                            : "text-muted-foreground hover:text-foreground"
                        )}
                        style={{ animationDelay: `${idx * 30}ms` }}
                        onClick={() => setMode(item.mode)}
                      >
                        <Icon className="h-4 w-4" />
                        <span>{t(locale, item.labelKey)}</span>
                      </button>
                    );
                  })}
                </nav>
              </section>
            );
          })}
        </div>

        <section className="surface-divider mt-4 pt-4">
          <div className="mb-2 flex items-center justify-between gap-2">
            <button
              className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground"
              onClick={() => setTasksExpanded((prev) => !prev)}
              type="button"
            >
              <span>{t(locale, "tasks.recent")}</span>
              {tasksExpanded ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
            </button>
            <div className="flex items-center gap-2">
              <span className="rounded-md border border-border/70 px-1.5 py-0.5 text-[10px] text-muted-foreground">
                {tasks.length}
              </span>
              {loadingTasks ? <RefreshCw className="h-3.5 w-3.5 animate-spin text-muted-foreground" /> : null}
            </div>
          </div>

          <div className={cn("overflow-hidden transition-all", tasksExpanded ? "max-h-[360px]" : "max-h-0")}>
            <div className="max-h-[320px] space-y-2 overflow-auto pr-1">
              <div className="flex items-center justify-between px-1 pb-1">
                <div className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={allSelected}
                    onChange={toggleSelectAll}
                    disabled={tasks.length === 0 || deletingTasks}
                  />
                  <span className="text-[11px] text-muted-foreground">{t(locale, "tasks.selected")}: {selectedTaskIds.length}</span>
                </div>
                <button
                  type="button"
                  className="rounded-lg border border-border/70 px-2 py-0.5 text-[11px] text-muted-foreground transition hover:border-[hsl(var(--ring))]/45 hover:text-foreground disabled:opacity-50"
                  disabled={deletingTasks || selectedTaskIds.length === 0}
                  onClick={() => setConfirmDeleteOpen(true)}
                >
                  {deletingTasks ? t(locale, "tasks.deleting") : t(locale, "tasks.deleteSelected")}
                </button>
              </div>
              <div className="grid gap-1 px-1 pb-1">
                <select
                  className="h-8 rounded-md border border-white/10 bg-[rgba(7,8,10,0.82)] px-2 text-[11px] text-foreground"
                  value={moveTargetFolderId}
                  onChange={(e) => setMoveTargetFolderId(e.target.value)}
                  disabled={deletingTasks || selectedTaskIds.length === 0}
                >
                  <option value="">{t(locale, "tasks.moveSelectFolder")}</option>
                  {folderOptions.map((folder) => (
                    <option key={folder.id} value={folder.id}>
                      {folder.label}
                    </option>
                  ))}
                </select>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    className="rounded-lg border border-border/70 px-2 py-0.5 text-[11px] text-muted-foreground transition hover:border-[hsl(var(--ring))]/45 hover:text-foreground disabled:opacity-50"
                    disabled={deletingTasks || selectedTaskIds.length === 0 || !moveTargetFolderId}
                    onClick={moveSelectedToFolder}
                  >
                    {t(locale, "tasks.moveToFolder")}
                  </button>
                  <button
                    type="button"
                    className="rounded-lg border border-border/70 px-2 py-0.5 text-[11px] text-muted-foreground transition hover:border-[hsl(var(--ring))]/45 hover:text-foreground disabled:opacity-50"
                    disabled={deletingTasks || selectedTaskIds.length === 0}
                    onClick={moveSelectedToRoot}
                  >
                    {t(locale, "tasks.moveToRoot")}
                  </button>
                </div>
              </div>
              {confirmDeleteOpen ? (
                  <div className="rounded-lg border border-rose-400/35 bg-rose-500/10 p-2 text-[11px] text-rose-100">
                  <p>{t(locale, "tasks.deletePrompt", { count: selectedTaskIds.length })}</p>
                  <div className="mt-2 flex items-center gap-2">
                    <button
                      type="button"
                      className="rounded border border-border/70 px-2 py-0.5 text-[11px] text-muted-foreground"
                      onClick={() => setConfirmDeleteOpen(false)}
                      disabled={deletingTasks}
                    >
                      {t(locale, "tasks.cancel")}
                    </button>
                    <button
                      type="button"
                      className="rounded border border-rose-300/45 px-2 py-0.5 text-[11px] text-rose-100 disabled:opacity-50"
                      onClick={deleteSelected}
                      disabled={deletingTasks}
                    >
                      {t(locale, "tasks.confirmDelete")}
                    </button>
                  </div>
                </div>
              ) : null}
              {tasks.length === 0 ? (
                <p className="text-xs text-muted-foreground">{t(locale, "tasks.noTasks")}</p>
              ) : (
                tasks.map((task) => (
                  <div
                    key={task.id}
                      className={cn(
                        "nav-item w-full rounded-lg px-2 py-1.5 text-left transition",
                      activeTaskId === task.id
                        ? "nav-item-active"
                        : "hover:border-[hsl(var(--ring))]/40"
                    )}
                  >
                    <div className="flex items-start gap-2">
                      <input
                        type="checkbox"
                        className="mt-0.5"
                        checked={selectedTaskIds.includes(task.id)}
                        onChange={() => toggleTaskSelection(task.id)}
                      />
                      <button type="button" className="w-full text-left" onClick={() => onTaskSelect(task)}>
                        <p className="truncate text-xs font-medium">#{task.id} {task.title}</p>
                        <div className="mt-0.5 flex items-center justify-between">
                          <p className={cn("text-[11px]", taskStatusClass(task.status))}>{localizeTaskStatus(task.status, locale)}</p>
                          {activeTaskId === task.id ? <span className="text-[10px] text-foreground">{t(locale, "tasks.selected")}</span> : null}
                        </div>
                      </button>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </section>
      </div>
    </aside>
  );
}
