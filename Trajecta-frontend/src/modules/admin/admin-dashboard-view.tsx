import { useEffect, useMemo, useRef, useState } from "react";
import { ShieldCheck } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import {
  ApiClientError,
  banAdminUser,
  clearAdminUserCache,
  deleteAdminUser,
  getAdminCacheHealth,
  getAdminRuntimeMetrics,
  getAdminServiceHealth,
  getAdminBroadcastHistory,
  getAdminUserDetails,
  getAdminUserStats,
  getAdminUsers,
  previewAdminBroadcastAudience,
  sendAdminBroadcast,
  unbanAdminPunishmentById,
  unbanAdminUserByUserId,
  updateAdminUserRole
} from "@/lib/api";
import { formatDateByLocale, localizeNotificationType, t } from "@/lib/i18n";
import { useLocaleStore } from "@/store/locale-store";
import { useFlightStore } from "@/store/flight-store";
import type { AdminUserDetails, AdminUserStats, AuthUser, NotificationInfo } from "@/types/flight";

type AdminSection = "users" | "user" | "notifications";

type AdminDashboardViewProps = {
  section: AdminSection;
};

type DateTimeParts = {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
};

function pad2(value: number) {
  return String(value).padStart(2, "0");
}

function toLocalDateTimeValue(parts: DateTimeParts) {
  return `${parts.year}-${pad2(parts.month)}-${pad2(parts.day)}T${pad2(parts.hour)}:${pad2(parts.minute)}`;
}

function parseLocalDateTimeValue(value: string): DateTimeParts | null {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return {
    year: date.getFullYear(),
    month: date.getMonth() + 1,
    day: date.getDate(),
    hour: date.getHours(),
    minute: date.getMinutes()
  };
}

function toIsoFromLocalDateTime(value: string): string | null {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return date.toISOString();
}

function daysInMonth(year: number, month: number) {
  return new Date(year, month, 0).getDate();
}

function localeTag(locale: "en" | "uk") {
  if (locale === "uk") {
    return "uk-UA";
  }
  return "en-US";
}

function normalizeRole(role: string | null | undefined): string {
  if (!role) {
    return "";
  }
  return role.toUpperCase().replace(/^ROLE_/, "");
}

export function AdminDashboardView({ section }: AdminDashboardViewProps) {
  const auth = useFlightStore((s) => s.auth);
  const setMode = useFlightStore((s) => s.setMode);
  const selectedUserId = useFlightStore((s) => s.adminSelectedUserId);
  const setSelectedUserId = useFlightStore((s) => s.setAdminSelectedUserId);
  const setError = useFlightStore((s) => s.setError);
  const locale = useLocaleStore((s) => s.locale);

  const [users, setUsers] = useState<AuthUser[]>([]);
  const [userStats, setUserStats] = useState<AdminUserStats | null>(null);
  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(false);
  const [userStatsLoading, setUserStatsLoading] = useState(false);

  const [selectedDetails, setSelectedDetails] = useState<AdminUserDetails | null>(null);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [banReason, setBanReason] = useState("");
  const [banUntil, setBanUntil] = useState("");
  const [banPickerOpen, setBanPickerOpen] = useState(false);
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);

  const [broadcastText, setBroadcastText] = useState("");
  const [broadcastType, setBroadcastType] = useState<"SYSTEM_NEWS" | "SYSTEM_ALERT">("SYSTEM_NEWS");
  const [recipientIdsRaw, setRecipientIdsRaw] = useState("");
  const [broadcastLoading, setBroadcastLoading] = useState(false);
  const [broadcastResult, setBroadcastResult] = useState<string | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewResult, setPreviewResult] = useState<{
    allUsers: boolean;
    totalUsers: number;
    targetUsers: number;
    targetUserIds: number[];
    missingRecipientIds: number[];
  } | null>(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [history, setHistory] = useState<NotificationInfo[]>([]);
  const [cacheHealthLoading, setCacheHealthLoading] = useState(false);
  const [cacheHealth, setCacheHealth] = useState<{ status: string; redisPing: string; error: string | null } | null>(null);
  const [serviceHealthLoading, setServiceHealthLoading] = useState(false);
  const [serviceHealth, setServiceHealth] = useState<{
    status: string;
    database: string;
    redis: string;
    databaseError: string | null;
    redisError: string | null;
    checkedAt: string;
  } | null>(null);
  const [runtimeLoading, setRuntimeLoading] = useState(false);
  const [runtimeError, setRuntimeError] = useState<string | null>(null);
  const [runtimeMetrics, setRuntimeMetrics] = useState<{
    status: string;
    stability: string;
    uptimeSeconds: number;
    activeThreads: number;
    heapUsedMb: number;
    heapMaxMb: number;
    usersTotal: number;
    tasksTotal: number;
    tasksFailed: number;
    taskFailureRatePct: number;
    checkedAt: string;
  } | null>(null);
  const [cacheUserIdRaw, setCacheUserIdRaw] = useState("");
  const [cacheClearLoading, setCacheClearLoading] = useState(false);
  const [cacheClearResult, setCacheClearResult] = useState<string | null>(null);
  const banPickerRef = useRef<HTMLDivElement | null>(null);

  const isUsersSection = section === "users";
  const isUserSection = section === "user";
  const isNotificationsSection = section === "notifications";

  const actorRole = normalizeRole(auth.user?.role);
  const targetRole = normalizeRole(selectedDetails?.role);
  const isSelfTarget = selectedDetails?.id === auth.user?.id;
  const canMutateTarget = Boolean(selectedDetails) && !isSelfTarget && (actorRole === "OWNER" || targetRole !== "OWNER");
  const hasActiveBan = Boolean(selectedDetails?.activePunishments.some((item) => item.type === "BAN" && !item.expired));

  const pickerParts = useMemo<DateTimeParts>(() => {
    const parsed = parseLocalDateTimeValue(banUntil);
    if (parsed) {
      return parsed;
    }
    const seed = new Date(Date.now() + 60 * 60 * 1000);
    return {
      year: seed.getFullYear(),
      month: seed.getMonth() + 1,
      day: seed.getDate(),
      hour: seed.getHours(),
      minute: Math.floor(seed.getMinutes() / 5) * 5
    };
  }, [banUntil]);

  function formatStatValue(value: number | null | undefined) {
    if (value == null) {
      return userStatsLoading ? t(locale, "common.loading") : "0";
    }
    return value.toLocaleString(localeTag(locale));
  }

  useEffect(() => {
    if (!auth.token || (!isUsersSection && !isUserSection)) {
      return;
    }

    let active = true;
    const run = async () => {
      setLoading(true);
      try {
        const usersPage = await getAdminUsers({ token: auth.token, page, size });
        if (!active) {
          return;
        }
        setUsers(usersPage.items);
        setHasNext(usersPage.hasNext);
        if (usersPage.items.length > 0 && selectedUserId === null) {
          setSelectedUserId(usersPage.items[0].id);
        }
      } catch (error) {
        if (!active) {
          return;
        }
        setError(error instanceof ApiClientError ? error.message : "Failed to load users", "admin");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    void run();
    return () => {
      active = false;
    };
  }, [auth.token, isUserSection, isUsersSection, page, selectedUserId, setError, setSelectedUserId, size]);

  useEffect(() => {
    if (!auth.token) {
      return;
    }

    let active = true;
    const load = async () => {
      setUserStatsLoading(true);
      try {
        const stats = await getAdminUserStats({ token: auth.token });
        if (active) {
          setUserStats(stats);
        }
      } catch (error) {
        if (active) {
          setError(error instanceof ApiClientError ? error.message : "Failed to load user stats", "admin");
        }
      } finally {
        if (active) {
          setUserStatsLoading(false);
        }
      }
    };

    void load();
    return () => {
      active = false;
    };
  }, [auth.token, section, setError]);

  useEffect(() => {
    if (!auth.token || selectedUserId === null || !isUserSection) {
      return;
    }

    let active = true;
    const run = async () => {
      setDetailsLoading(true);
      try {
        const details = await getAdminUserDetails({ token: auth.token, userId: selectedUserId });
        if (active) {
          setSelectedDetails(details);
        }
      } catch (error) {
        if (active) {
          setError(error instanceof ApiClientError ? error.message : "Failed to load user details", "admin");
        }
      } finally {
        if (active) {
          setDetailsLoading(false);
        }
      }
    };

    void run();
    return () => {
      active = false;
    };
  }, [auth.token, isUserSection, selectedUserId, setError]);

  useEffect(() => {
    if (!auth.token) {
      return;
    }

    let active = true;
    const load = async () => {
      setServiceHealthLoading(true);
      setRuntimeLoading(true);
      setRuntimeError(null);
      try {
        const [health, metrics] = await Promise.all([
          getAdminServiceHealth({ token: auth.token }),
          getAdminRuntimeMetrics({ token: auth.token })
        ]);
        if (active) {
          setServiceHealth(health);
          setRuntimeMetrics(metrics);
        }
      } catch (error) {
        if (active) {
          setRuntimeError(error instanceof ApiClientError ? error.message : "Failed to load runtime metrics");
          setError(error instanceof ApiClientError ? error.message : "Failed to load service health", "admin");
        }
      } finally {
        if (active) {
          setServiceHealthLoading(false);
          setRuntimeLoading(false);
        }
      }
    };

    void load();
    return () => {
      active = false;
    };
  }, [auth.token, section, setError]);

  useEffect(() => {
    if (!auth.token || !isNotificationsSection) {
      return;
    }

    let active = true;
    const load = async () => {
      setCacheHealthLoading(true);
      setHistoryLoading(true);
      try {
        const [health, items] = await Promise.all([
          getAdminCacheHealth({ token: auth.token }),
          getAdminBroadcastHistory({ token: auth.token, limit: 30 })
        ]);
        if (!active) {
          return;
        }
        setCacheHealth(health);
        setHistory(items);
      } catch (error) {
        if (active) {
          setError(error instanceof ApiClientError ? error.message : "Failed to load notifications admin data", "admin");
        }
      } finally {
        if (active) {
          setCacheHealthLoading(false);
          setHistoryLoading(false);
        }
      }
    };

    void load();
    return () => {
      active = false;
    };
  }, [auth.token, isNotificationsSection, setError]);

  useEffect(() => {
    if (!banPickerOpen) {
      return;
    }

    const onPointerDown = (event: PointerEvent) => {
      const root = banPickerRef.current;
      if (!root) {
        return;
      }
      if (event.target instanceof Node && !root.contains(event.target)) {
        setBanPickerOpen(false);
      }
    };

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setBanPickerOpen(false);
      }
    };

    window.addEventListener("pointerdown", onPointerDown);
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("pointerdown", onPointerDown);
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [banPickerOpen]);

  useEffect(() => {
    if (!confirmDeleteOpen) {
      return;
    }

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setConfirmDeleteOpen(false);
      }
    };

    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [confirmDeleteOpen]);

  async function refreshUsersAndDetails() {
    if (!auth.token || (!isUsersSection && !isUserSection)) {
      return;
    }

    const [usersPage, stats, details] = await Promise.all([
      getAdminUsers({ token: auth.token, page, size }),
      getAdminUserStats({ token: auth.token }),
      selectedUserId !== null
        ? getAdminUserDetails({ token: auth.token, userId: selectedUserId })
        : Promise.resolve(null)
    ]);
    setUsers(usersPage.items);
    setHasNext(usersPage.hasNext);
    setUserStats(stats);

    if (details) {
      setSelectedDetails(details);
    }
  }

  function parseRecipientIds(raw: string) {
    return raw
      .split(",")
      .map((value) => Number.parseInt(value.trim(), 10))
      .filter((value) => Number.isFinite(value) && value > 0);
  }

  function updateBanPicker(patch: Partial<DateTimeParts>) {
    const next = { ...pickerParts, ...patch };
    const maxDay = daysInMonth(next.year, next.month);
    if (next.day > maxDay) {
      next.day = maxDay;
    }
    setBanUntil(toLocalDateTimeValue(next));
  }

  function applyBanDuration(hours: number) {
    const date = new Date(Date.now() + hours * 60 * 60 * 1000);
    const next: DateTimeParts = {
      year: date.getFullYear(),
      month: date.getMonth() + 1,
      day: date.getDate(),
      hour: date.getHours(),
      minute: Math.floor(date.getMinutes() / 5) * 5
    };
    setBanUntil(toLocalDateTimeValue(next));
  }

  async function handleRoleUpdate(role: "USER" | "ADMIN") {
    if (!auth.token || selectedUserId === null || !canMutateTarget) {
      return;
    }
    setActionLoading(true);
    try {
      await updateAdminUserRole({ token: auth.token, userId: selectedUserId, role });
      await refreshUsersAndDetails();
    } catch (error) {
      setError(error instanceof ApiClientError ? error.message : "Failed to update role", "admin");
    } finally {
      setActionLoading(false);
    }
  }

  async function handleBanUser() {
    if (!auth.token || selectedUserId === null || !canMutateTarget) {
      return;
    }
    if (!banReason.trim()) {
      setError("Ban reason is required", "admin");
      return;
    }

    const banIso = banUntil ? toIsoFromLocalDateTime(banUntil) : null;
    if (banUntil && !banIso) {
      setError("Invalid ban expiration date", "admin");
      return;
    }

    setActionLoading(true);
    try {
      await banAdminUser({
        token: auth.token,
        userId: selectedUserId,
        reason: banReason.trim(),
        expiredAt: banIso
      });
      setBanReason("");
      setBanUntil("");
      setBanPickerOpen(false);
      await refreshUsersAndDetails();
    } catch (error) {
      setError(error instanceof ApiClientError ? error.message : "Failed to ban user", "admin");
    } finally {
      setActionLoading(false);
    }
  }

  async function handleUnbanUser() {
    if (!auth.token || selectedUserId === null || !canMutateTarget) {
      return;
    }
    setActionLoading(true);
    try {
      await unbanAdminUserByUserId({ token: auth.token, userId: selectedUserId });
      await refreshUsersAndDetails();
    } catch (error) {
      setError(error instanceof ApiClientError ? error.message : "Failed to unban user", "admin");
    } finally {
      setActionLoading(false);
    }
  }

  async function handleUnbanPunishment(punishmentId: number) {
    if (!auth.token || !canMutateTarget) {
      return;
    }
    setActionLoading(true);
    try {
      await unbanAdminPunishmentById({ token: auth.token, punishmentId });
      await refreshUsersAndDetails();
    } catch (error) {
      setError(error instanceof ApiClientError ? error.message : "Failed to unban user", "admin");
    } finally {
      setActionLoading(false);
    }
  }

  async function handleConfirmDeleteUser() {
    if (!auth.token || selectedUserId === null || !canMutateTarget) {
      return;
    }
    setActionLoading(true);
    try {
      await deleteAdminUser({ token: auth.token, userId: selectedUserId });
      const [usersPage, stats] = await Promise.all([
        getAdminUsers({ token: auth.token, page, size }),
        getAdminUserStats({ token: auth.token })
      ]);
      setUsers(usersPage.items);
      setHasNext(usersPage.hasNext);
      setUserStats(stats);
      setSelectedUserId(null);
      setSelectedDetails(null);
      setConfirmDeleteOpen(false);
      setMode("admin-users");
    } catch (error) {
      setError(error instanceof ApiClientError ? error.message : "Failed to delete user", "admin");
    } finally {
      setActionLoading(false);
    }
  }

  async function handlePreview() {
    if (!auth.token) {
      return;
    }

    setPreviewLoading(true);
    setPreviewResult(null);
    try {
      const preview = await previewAdminBroadcastAudience({
        token: auth.token,
        content: broadcastText.trim() || "preview",
        type: broadcastType,
        recipientIds: parseRecipientIds(recipientIdsRaw)
      });
      setPreviewResult(preview);
    } catch (error) {
      setError(error instanceof ApiClientError ? error.message : "Failed to preview broadcast audience", "admin");
    } finally {
      setPreviewLoading(false);
    }
  }

  async function handleBroadcast() {
    if (!auth.token) {
      return;
    }

    const content = broadcastText.trim();
    if (!content) {
      setError("Notification text is required", "admin");
      return;
    }

    setBroadcastLoading(true);
    setBroadcastResult(null);
    try {
      const sent = await sendAdminBroadcast({
        token: auth.token,
        content,
        type: broadcastType,
        recipientIds: parseRecipientIds(recipientIdsRaw)
      });
      setBroadcastResult(`Sent to ${sent} users`);
      setBroadcastText("");
      setRecipientIdsRaw("");
      setPreviewResult(null);
      const items = await getAdminBroadcastHistory({ token: auth.token, limit: 30 });
      setHistory(items);
    } catch (error) {
      setError(error instanceof ApiClientError ? error.message : "Failed to send broadcast notification", "admin");
    } finally {
      setBroadcastLoading(false);
    }
  }

  async function handleRefreshCacheHealth() {
    if (!auth.token) {
      return;
    }

    setCacheHealthLoading(true);
    try {
      const health = await getAdminCacheHealth({ token: auth.token });
      setCacheHealth(health);
    } catch (error) {
      setError(error instanceof ApiClientError ? error.message : "Failed to refresh cache health", "admin");
    } finally {
      setCacheHealthLoading(false);
    }
  }

  async function handleRefreshServiceHealth() {
    if (!auth.token) {
      return;
    }

    setServiceHealthLoading(true);
    setRuntimeLoading(true);
    setRuntimeError(null);
    try {
      const [health, metrics] = await Promise.all([
        getAdminServiceHealth({ token: auth.token }),
        getAdminRuntimeMetrics({ token: auth.token })
      ]);
      setServiceHealth(health);
      setRuntimeMetrics(metrics);
    } catch (error) {
      setRuntimeError(error instanceof ApiClientError ? error.message : "Failed to load runtime metrics");
      setError(error instanceof ApiClientError ? error.message : "Failed to refresh service health", "admin");
    } finally {
      setServiceHealthLoading(false);
      setRuntimeLoading(false);
    }
  }

  async function handleClearUserCache() {
    if (!auth.token) {
      return;
    }

    const userId = Number.parseInt(cacheUserIdRaw.trim(), 10);
    if (!Number.isFinite(userId) || userId <= 0) {
      setError("Provide a valid user id for cache clear", "admin");
      return;
    }

    setCacheClearLoading(true);
    setCacheClearResult(null);
    try {
      const result = await clearAdminUserCache({ token: auth.token, userId });
      setCacheClearResult(`Cleared user #${result.userId}. exists=${result.userExists}, usernameKeyEvicted=${result.usernameKeyEvicted}`);
    } catch (error) {
      setError(error instanceof ApiClientError ? error.message : "Failed to clear user cache", "admin");
    } finally {
      setCacheClearLoading(false);
    }
  }

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ShieldCheck className="h-4 w-4 text-accent" />
            {t(locale, "admin.title")}
          </CardTitle>
        </CardHeader>
        <CardContent className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <div className="rounded-lg border border-border bg-background/40 p-3">
            <p className="text-xs uppercase text-muted-foreground">{t(locale, "admin.totalUsers")}</p>
            <p className="mt-1 text-2xl font-semibold text-foreground">{formatStatValue(userStats?.totalUsers)}</p>
          </div>
          <div className="rounded-lg border border-border bg-background/40 p-3">
            <p className="text-xs uppercase text-muted-foreground">{t(locale, "admin.userAccounts")}</p>
            <p className="mt-1 text-2xl font-semibold text-foreground">{formatStatValue(userStats?.usersCount)}</p>
          </div>
          <div className="rounded-lg border border-border bg-background/40 p-3">
            <p className="text-xs uppercase text-muted-foreground">{t(locale, "admin.admins")}</p>
            <p className="mt-1 text-2xl font-semibold text-foreground">{formatStatValue(userStats?.adminsCount)}</p>
          </div>
          <div className="rounded-lg border border-border bg-background/40 p-3">
            <p className="text-xs uppercase text-muted-foreground">{t(locale, "admin.owners")}</p>
            <p className="mt-1 text-2xl font-semibold text-foreground">{formatStatValue(userStats?.ownersCount)}</p>
          </div>
          <div className="rounded-lg border border-border bg-background/40 p-3">
            <p className="text-xs uppercase text-muted-foreground">{t(locale, "admin.staff")}</p>
            <p className="mt-1 text-2xl font-semibold text-foreground">{formatStatValue(userStats?.staffCount)}</p>
          </div>
          <div className="rounded-lg border border-border bg-background/40 p-3">
            <p className="text-xs uppercase text-muted-foreground">{t(locale, "admin.bannedUsers")}</p>
            <p className="mt-1 text-2xl font-semibold text-foreground">{formatStatValue(userStats?.bannedUsers)}</p>
          </div>
          <div className="rounded-lg border border-border bg-background/40 p-3">
            <p className="text-xs uppercase text-muted-foreground">{t(locale, "common.status")}</p>
            <p className="mt-1 text-sm font-semibold text-foreground">
              {serviceHealthLoading
                ? t(locale, "common.loading")
                : serviceHealth?.status ?? (loading ? t(locale, "common.loading") : t(locale, "common.ready"))}
            </p>
            {serviceHealth ? (
              <p className="mt-1 text-[11px] text-muted-foreground">
                DB: {serviceHealth.database} | Redis: {serviceHealth.redis}
              </p>
            ) : null}
            <div className="mt-2">
              <Button variant="outline" size="sm" onClick={() => void handleRefreshServiceHealth()} disabled={serviceHealthLoading || runtimeLoading}>
                {serviceHealthLoading || runtimeLoading ? t(locale, "admin.refreshing") : t(locale, "admin.refreshService")}
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t(locale, "admin.runtimeMetrics")}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {runtimeLoading && !runtimeMetrics ? (
            <p className="text-xs text-muted-foreground">{t(locale, "common.loading")}</p>
          ) : runtimeMetrics ? (
            <div className="grid gap-3 md:grid-cols-4">
              <div className="rounded-lg border border-border bg-background/40 p-3">
                <p className="text-xs uppercase text-muted-foreground">{t(locale, "admin.stability")}</p>
                <p className="mt-1 text-sm font-semibold text-foreground">{runtimeMetrics.stability}</p>
              </div>
              <div className="rounded-lg border border-border bg-background/40 p-3">
                <p className="text-xs uppercase text-muted-foreground">{t(locale, "admin.heap")}</p>
                <p className="mt-1 text-sm font-semibold text-foreground">{runtimeMetrics.heapUsedMb} / {runtimeMetrics.heapMaxMb} MB</p>
              </div>
              <div className="rounded-lg border border-border bg-background/40 p-3">
                <p className="text-xs uppercase text-muted-foreground">{t(locale, "admin.tasksStats")}</p>
                <p className="mt-1 text-sm font-semibold text-foreground">
                  {runtimeMetrics.tasksFailed}/{runtimeMetrics.tasksTotal} ({runtimeMetrics.taskFailureRatePct}%)
                </p>
              </div>
              <div className="rounded-lg border border-border bg-background/40 p-3">
                <p className="text-xs uppercase text-muted-foreground">{t(locale, "admin.runtime")}</p>
                <p className="mt-1 text-sm font-semibold text-foreground">
                  {runtimeMetrics.uptimeSeconds}s | {runtimeMetrics.activeThreads} th
                </p>
              </div>
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">Runtime metrics are not available yet.</p>
          )}

          {runtimeError ? <p className="text-xs text-red-300">{runtimeError}</p> : null}
        </CardContent>
      </Card>

      {isUsersSection ? (
        <Card>
          <CardHeader>
            <CardTitle>{t(locale, "admin.userAccounts")}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="overflow-auto rounded-lg border border-border">
              <table className="w-full min-w-[680px] text-sm">
                <thead className="bg-background/60 text-xs uppercase text-muted-foreground">
                  <tr>
                    <th className="px-3 py-2 text-left">{t(locale, "admin.userId")}</th>
                    <th className="px-3 py-2 text-left">{t(locale, "admin.userName")}</th>
                    <th className="px-3 py-2 text-left">{t(locale, "admin.userUsername")}</th>
                    <th className="px-3 py-2 text-left">{t(locale, "admin.userEmail")}</th>
                    <th className="px-3 py-2 text-left">{t(locale, "admin.userRole")}</th>
                    <th className="px-3 py-2 text-left">{t(locale, "common.status")}</th>
                    <th className="px-3 py-2 text-left">{t(locale, "admin.userActions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((user) => (
                    <tr key={user.id} className={`border-t border-border/70 ${selectedUserId === user.id ? "bg-background/50" : ""}`}>
                      <td className="px-3 py-2">{user.id}</td>
                      <td className="px-3 py-2">{user.name}</td>
                      <td className="px-3 py-2">{user.username}</td>
                      <td className="px-3 py-2">{user.email}</td>
                      <td className="px-3 py-2">{user.role}</td>
                      <td className="px-3 py-2">
                        {user.hasActiveBan ? (
                          <span className="inline-flex items-center rounded-full border border-rose-300/40 bg-rose-500/15 px-2 py-0.5 text-[11px] text-rose-100">
                            {t(locale, "admin.statusBanned")}
                          </span>
                        ) : (
                          <span className="text-xs text-muted-foreground">{t(locale, "admin.statusClean")}</span>
                        )}
                      </td>
                      <td className="px-3 py-2">
                        <Button
                          variant="outline"
                          onClick={() => {
                            setSelectedUserId(user.id);
                            setMode("admin-user");
                          }}
                        >
                          {t(locale, "admin.select")}
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="flex items-center justify-between">
              <Button variant="outline" disabled={page === 0 || loading} onClick={() => setPage((p) => Math.max(0, p - 1))}>{t(locale, "admin.prev")}</Button>
              <span className="text-xs text-muted-foreground">{t(locale, "admin.page", { page: page + 1 })}</span>
              <Button variant="outline" disabled={!hasNext || loading} onClick={() => setPage((p) => p + 1)}>{t(locale, "admin.next")}</Button>
            </div>
          </CardContent>
        </Card>
      ) : null}

      {isUserSection ? (
        <Card>
          <CardHeader>
            <CardTitle>{t(locale, "admin.controlsTitle")}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {selectedUserId === null ? (
              <div className="rounded-lg border border-border bg-background/30 p-3 text-sm text-muted-foreground">
                <p>{t(locale, "admin.selectUserHint")}</p>
                <Button className="mt-3" variant="outline" onClick={() => setMode("admin-users")}>{t(locale, "admin.userAccounts")}</Button>
              </div>
            ) : detailsLoading ? (
              <p className="text-xs text-muted-foreground">{t(locale, "admin.loadingDetails")}</p>
            ) : selectedDetails ? (
              <div className="space-y-3">
                <div className="rounded-md border border-border/70 bg-background/40 p-2 text-xs text-muted-foreground">
                  <p><span className="text-foreground">{t(locale, "common.user")}:</span> #{selectedDetails.id} {selectedDetails.username}</p>
                  <p><span className="text-foreground">{t(locale, "common.name")}:</span> {selectedDetails.name}</p>
                  <p><span className="text-foreground">{t(locale, "common.role")}:</span> {selectedDetails.role}</p>
                </div>

                {!canMutateTarget ? (
                  <p className="rounded-md border border-rose-300/40 bg-rose-500/15 px-3 py-2 text-xs text-rose-100">
                    {isSelfTarget
                      ? t(locale, "admin.selfActionBlocked")
                      : t(locale, "admin.ownerProtected")}
                  </p>
                ) : null}

                <div className="space-y-2 rounded-md border border-border/70 p-2">
                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t(locale, "admin.roleManagement")}</p>
                  <div className="flex flex-wrap gap-2">
                    <Button variant="outline" disabled={actionLoading || !canMutateTarget} onClick={() => void handleRoleUpdate("USER")}>{t(locale, "admin.setRoleUser")}</Button>
                    <Button variant="outline" disabled={actionLoading || !canMutateTarget} onClick={() => void handleRoleUpdate("ADMIN")}>{t(locale, "admin.setRoleAdmin")}</Button>
                  </div>
                </div>

                <div className="space-y-2 rounded-md border border-border/70 p-2">
                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{t(locale, "admin.banManagement")}</p>
                  <div className="grid gap-2 md:grid-cols-[1fr_auto_auto]">
                    <input
                      className="ui-field text-sm"
                      placeholder={t(locale, "admin.banReason")}
                      value={banReason}
                      onChange={(e) => setBanReason(e.target.value)}
                      disabled={!canMutateTarget || hasActiveBan}
                    />

                    <div ref={banPickerRef} className="relative">
                      <Button
                        variant="outline"
                        disabled={!canMutateTarget || hasActiveBan}
                        onClick={() => setBanPickerOpen((v) => !v)}
                      >
                        {banUntil ? formatDateByLocale(new Date(banUntil).toISOString(), locale) : t(locale, "admin.pickBanUntil")}
                      </Button>
                      {banPickerOpen ? (
                        <div className="absolute right-0 z-20 mt-2 w-[340px] rounded-lg border border-border bg-background p-3 shadow-lg">
                          <p className="mb-2 text-xs text-muted-foreground">{t(locale, "admin.pickBanUntil")}</p>
                          <div className="grid grid-cols-5 gap-2">
                            <select className="ui-select" value={pickerParts.year} onChange={(e) => updateBanPicker({ year: Number.parseInt(e.target.value, 10) })}>
                              {Array.from({ length: 4 }, (_, idx) => new Date().getFullYear() + idx).map((year) => (
                                <option key={year} value={year}>{year}</option>
                              ))}
                            </select>
                            <select className="ui-select" value={pickerParts.month} onChange={(e) => updateBanPicker({ month: Number.parseInt(e.target.value, 10) })}>
                              {Array.from({ length: 12 }, (_, idx) => idx + 1).map((month) => (
                                <option key={month} value={month}>
                                  {new Date(2026, month - 1, 1).toLocaleString(localeTag(locale), { month: "short" })}
                                </option>
                              ))}
                            </select>
                            <select className="ui-select" value={pickerParts.day} onChange={(e) => updateBanPicker({ day: Number.parseInt(e.target.value, 10) })}>
                              {Array.from({ length: daysInMonth(pickerParts.year, pickerParts.month) }, (_, idx) => idx + 1).map((day) => (
                                <option key={day} value={day}>{day}</option>
                              ))}
                            </select>
                            <select className="ui-select" value={pickerParts.hour} onChange={(e) => updateBanPicker({ hour: Number.parseInt(e.target.value, 10) })}>
                              {Array.from({ length: 24 }, (_, idx) => idx).map((hour) => (
                                <option key={hour} value={hour}>{pad2(hour)}</option>
                              ))}
                            </select>
                            <select className="ui-select" value={pickerParts.minute} onChange={(e) => updateBanPicker({ minute: Number.parseInt(e.target.value, 10) })}>
                              {Array.from({ length: 12 }, (_, idx) => idx * 5).map((minute) => (
                                <option key={minute} value={minute}>{pad2(minute)}</option>
                              ))}
                            </select>
                          </div>
                          <div className="mt-2 flex flex-wrap gap-2">
                            <Button variant="outline" size="sm" onClick={() => applyBanDuration(24)}>{t(locale, "admin.plus24h")}</Button>
                            <Button variant="outline" size="sm" onClick={() => applyBanDuration(24 * 7)}>{t(locale, "admin.plus7d")}</Button>
                            <Button variant="outline" size="sm" onClick={() => setBanUntil("")}>{t(locale, "admin.clearBanUntil")}</Button>
                          </div>
                        </div>
                      ) : null}
                    </div>

                    {!hasActiveBan ? (
                      <Button disabled={actionLoading || !canMutateTarget} onClick={() => void handleBanUser()}>{t(locale, "admin.banUser")}</Button>
                    ) : (
                      <Button variant="outline" disabled={actionLoading || !canMutateTarget} onClick={() => void handleUnbanUser()}>{t(locale, "admin.unbanUser")}</Button>
                    )}
                  </div>
                  <p className="text-[11px] text-muted-foreground">
                    {hasActiveBan ? t(locale, "admin.banStateActive") : t(locale, "admin.banStateInactive")}
                  </p>
                </div>

                <div className="space-y-2 rounded-md border border-rose-300/30 bg-rose-500/10 p-2">
                  <p className="text-xs font-semibold uppercase tracking-wide text-rose-100">{t(locale, "admin.dangerZone")}</p>
                  <Button
                    variant="destructive"
                    disabled={actionLoading || !canMutateTarget}
                    onClick={() => setConfirmDeleteOpen(true)}
                  >
                    {t(locale, "admin.deleteUser")}
                  </Button>
                </div>

                <div>
                  <p className="mb-1 text-xs text-muted-foreground">{t(locale, "admin.activePunishments")}</p>
                  {selectedDetails.activePunishments.length === 0 ? (
                    <p className="text-xs text-muted-foreground">{t(locale, "admin.noPunishments")}</p>
                  ) : (
                    <div className="space-y-1">
                      {selectedDetails.activePunishments.map((p) => (
                        <div key={p.id} className="rounded border border-border/70 p-2 text-xs">
                          <p className="font-medium">{p.type} • {p.reason}</p>
                          <p className="text-muted-foreground">{t(locale, "common.until")}: {p.expiredAt ? formatDateByLocale(p.expiredAt, locale) : t(locale, "common.permanent")}</p>
                          {p.type === "BAN" && !p.expired ? (
                            <div className="mt-2">
                              <Button
                                variant="outline"
                                size="sm"
                                disabled={actionLoading || !canMutateTarget}
                                onClick={() => void handleUnbanPunishment(p.id)}
                              >
                                {t(locale, "admin.unbanUser")}
                              </Button>
                            </div>
                          ) : null}
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <div>
                  <p className="mb-1 text-xs text-muted-foreground">{t(locale, "admin.punishmentHistory")}</p>
                  {selectedDetails.punishmentHistory.length === 0 ? (
                    <p className="text-xs text-muted-foreground">{t(locale, "admin.noPunishmentHistory")}</p>
                  ) : (
                    <div className="space-y-1">
                      {selectedDetails.punishmentHistory.map((p) => (
                        <div key={`history-${p.id}`} className="rounded border border-border/70 p-2 text-xs">
                          <p className="font-medium">{p.type} • {p.reason}</p>
                          <p className="text-muted-foreground">{t(locale, "admin.issuedAt")}: {formatDateByLocale(p.createdAt, locale)}</p>
                          <p className="text-muted-foreground">{t(locale, "common.until")}: {p.expiredAt ? formatDateByLocale(p.expiredAt, locale) : t(locale, "common.permanent")}</p>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ) : (
              <p className="text-xs text-muted-foreground">{t(locale, "admin.selectUserHint")}</p>
            )}
          </CardContent>
        </Card>
      ) : null}

      {isNotificationsSection ? (
        <>
          <Card>
            <CardHeader>
              <CardTitle>{t(locale, "admin.cacheMaintenance")}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="rounded-md border border-border bg-background/40 p-2 text-xs text-muted-foreground">
                {cacheHealthLoading ? (
                  <p>{t(locale, "admin.checkingCache")}</p>
                ) : cacheHealth ? (
                  <p>
                    {t(locale, "common.status")}: <span className="font-medium text-foreground">{cacheHealth.status}</span> | Redis: {cacheHealth.redisPing}
                    {cacheHealth.error ? ` | ${cacheHealth.error}` : ""}
                  </p>
                ) : (
                  <p>{t(locale, "admin.cacheNotLoaded")}</p>
                )}
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <input
                  className="ui-field text-sm"
                  value={cacheUserIdRaw}
                  onChange={(e) => setCacheUserIdRaw(e.target.value)}
                  placeholder="User id"
                />
                <Button variant="outline" onClick={() => void handleRefreshCacheHealth()} disabled={cacheHealthLoading}>
                  {cacheHealthLoading ? t(locale, "admin.refreshing") : t(locale, "admin.refreshHealth")}
                </Button>
                <Button onClick={() => void handleClearUserCache()} disabled={cacheClearLoading || !cacheUserIdRaw.trim()}>
                  {cacheClearLoading ? t(locale, "admin.clearing") : t(locale, "admin.clearUserCache")}
                </Button>
              </div>
              {cacheClearResult ? <p className="text-xs text-foreground">{cacheClearResult}</p> : null}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>{t(locale, "admin.broadcast")}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <p className="text-xs text-muted-foreground">{t(locale, "admin.broadcastHint")}</p>
              <div className="grid gap-2 md:grid-cols-2">
                <label className="space-y-1 text-xs text-muted-foreground">
                  <span>{t(locale, "admin.type")}</span>
                  <select
                    className="ui-select"
                    value={broadcastType}
                    onChange={(e) => setBroadcastType(e.target.value as "SYSTEM_NEWS" | "SYSTEM_ALERT")}
                  >
                    <option value="SYSTEM_NEWS">{localizeNotificationType("SYSTEM_NEWS", locale)}</option>
                    <option value="SYSTEM_ALERT">{localizeNotificationType("SYSTEM_ALERT", locale)}</option>
                  </select>
                </label>
                <label className="space-y-1 text-xs text-muted-foreground">
                  <span>{t(locale, "admin.recipients")}</span>
                  <input
                    className="ui-field text-sm"
                    value={recipientIdsRaw}
                    onChange={(e) => setRecipientIdsRaw(e.target.value)}
                    placeholder={t(locale, "admin.recipientExample")}
                  />
                </label>
              </div>
              <textarea
                className="ui-field min-h-24"
                value={broadcastText}
                onChange={(e) => setBroadcastText(e.target.value)}
                maxLength={500}
                placeholder={t(locale, "admin.enterMessage")}
              />
              <div className="flex items-center gap-2">
                <Button variant="outline" onClick={() => void handlePreview()} disabled={previewLoading || broadcastLoading}>
                  {previewLoading ? t(locale, "admin.previewing") : t(locale, "admin.previewAudience")}
                </Button>
                <Button onClick={() => void handleBroadcast()} disabled={broadcastLoading || !broadcastText.trim()}>
                  {broadcastLoading ? t(locale, "admin.sending") : t(locale, "admin.sendBroadcast")}
                </Button>
                {broadcastResult ? <span className="text-xs text-foreground">{broadcastResult}</span> : null}
              </div>
              {previewResult ? (
                <div className="rounded-md border border-border bg-background/40 p-2 text-xs text-muted-foreground">
                  <p>
                    {t(locale, "admin.mode")}: {previewResult.allUsers ? t(locale, "admin.allUsers") : t(locale, "admin.selectedIds")} | {t(locale, "admin.targets")}: {previewResult.targetUsers} / {previewResult.totalUsers}
                  </p>
                  {previewResult.missingRecipientIds.length > 0 ? (
                    <p className="mt-1 text-foreground">{t(locale, "admin.missingIds")}: {previewResult.missingRecipientIds.join(", ")}</p>
                  ) : null}
                </div>
              ) : null}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>{t(locale, "admin.broadcastHistory")}</CardTitle>
            </CardHeader>
            <CardContent>
              {historyLoading ? (
                <p className="text-xs text-muted-foreground">{t(locale, "common.loading")}</p>
              ) : history.length === 0 ? (
                <p className="text-xs text-muted-foreground">{t(locale, "admin.noBroadcastHistory")}</p>
              ) : (
                <div className="space-y-2">
                  {history.map((item) => (
                    <div key={item.id} className="rounded-md border border-border/70 bg-background/40 p-2 text-xs">
                      <p className="font-medium text-foreground">{localizeNotificationType(item.type, locale)}</p>
                      <p className="text-muted-foreground">{item.content}</p>
                      <p className="mt-1 text-[11px] text-muted-foreground">
                        {t(locale, "admin.recipient")} #{item.recipientId ?? "-"} • {formatDateByLocale(item.createdAt, locale)}
                      </p>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </>
      ) : null}

      {confirmDeleteOpen && selectedDetails ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4" onClick={() => setConfirmDeleteOpen(false)}>
          <div className="w-full max-w-md rounded-xl border border-border bg-background p-4 shadow-xl" onClick={(event) => event.stopPropagation()}>
            <h3 className="text-base font-semibold text-foreground">{t(locale, "admin.deleteConfirmTitle")}</h3>
            <p className="mt-2 text-sm text-muted-foreground">
              {t(locale, "admin.deleteConfirmText", { username: selectedDetails.username })}
            </p>
            <div className="mt-4 flex items-center justify-end gap-2">
              <Button variant="outline" onClick={() => setConfirmDeleteOpen(false)}>{t(locale, "tasks.cancel")}</Button>
              <Button variant="destructive" disabled={actionLoading} onClick={() => void handleConfirmDeleteUser()}>
                {actionLoading ? t(locale, "common.loading") : t(locale, "tasks.confirmDelete")}
              </Button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
