import { useEffect, useState } from "react";
import { Save } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { t } from "@/lib/i18n";
import { useLocaleStore } from "@/store/locale-store";
import { ApiClientError, getPublicPing, updateCurrentUserProfile } from "@/lib/api";
import { useFlightStore } from "@/store/flight-store";
import type { UserProfileUpdateInput } from "@/types/flight";

export function ProfileView() {
  const auth = useFlightStore((s) => s.auth);
  const locale = useLocaleStore((s) => s.locale);
  const setAuthenticated = useFlightStore((s) => s.setAuthenticated);
  const setError = useFlightStore((s) => s.setError);

  const [name, setName] = useState(auth.user?.name ?? "");
  const [username, setUsername] = useState(auth.user?.username ?? "");
  const [email, setEmail] = useState(auth.user?.email ?? "");
  const [password, setPassword] = useState("");
  const [oldPassword, setOldPassword] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveResult, setSaveResult] = useState<{ kind: "success" | "error"; message: string } | null>(null);
  const [publicPing, setPublicPing] = useState<string>("-");

  const currentName = auth.user?.name ?? "";
  const currentUsername = auth.user?.username ?? "";
  const currentEmail = auth.user?.email ?? "";

  const normalizedName = name.trim();
  const normalizedUsername = username.trim();
  const normalizedEmail = email.trim();
  const normalizedPassword = password.trim();
  const normalizedOldPassword = oldPassword.trim();

  const hasChanges =
    (normalizedName.length > 0 && normalizedName !== currentName) ||
    (normalizedUsername.length > 0 && normalizedUsername !== currentUsername) ||
    (normalizedEmail.length > 0 && normalizedEmail !== currentEmail) ||
    normalizedPassword.length > 0;

  useEffect(() => {
    let active = true;
    void getPublicPing()
      .then((payload) => {
        if (active) {
          setPublicPing(payload.status || "ok");
        }
      })
      .catch(() => {
        if (active) {
          setPublicPing("unavailable");
        }
      });
    return () => {
      active = false;
    };
  }, []);

  function buildUpdatePayload(): UserProfileUpdateInput {
    const update: UserProfileUpdateInput = {};

    if (normalizedName.length > 0 && normalizedName !== currentName) {
      update.name = normalizedName;
    }
    if (normalizedUsername.length > 0 && normalizedUsername !== currentUsername) {
      update.username = normalizedUsername;
    }
    if (normalizedEmail.length > 0 && normalizedEmail !== currentEmail) {
      update.email = normalizedEmail;
    }
    if (normalizedPassword.length > 0) {
      update.password = normalizedPassword;
      update.oldPassword = normalizedOldPassword;
    }

    return update;
  }

  async function handleSave() {
    if (!auth.token) {
      return;
    }

    const updatePayload = buildUpdatePayload();
    if (Object.keys(updatePayload).length === 0) {
      setError("No changes to save", "profile");
      setSaveResult(null);
      return;
    }

    if (normalizedPassword.length > 0 && normalizedOldPassword.length === 0) {
      const message = t(locale, "profile.oldPasswordRequired");
      setError(message, "profile");
      setSaveResult({ kind: "error", message });
      return;
    }

    if (normalizedPassword.length > 0 && normalizedPassword.length < 8) {
      const message = t(locale, "profile.passwordTooShort");
      setError(message, "profile");
      setSaveResult({ kind: "error", message });
      return;
    }

    setSaving(true);
    setError(null, "profile");
    setSaveResult(null);
    try {
      const updated = await updateCurrentUserProfile({
        token: auth.token,
        update: updatePayload
      });
      setAuthenticated(auth.token, updated);
      setName(updated.name ?? "");
      setUsername(updated.username ?? "");
      setEmail(updated.email ?? "");
      setPassword("");
      setOldPassword("");
      setSaveResult({ kind: "success", message: t(locale, "profile.saved") });
    } catch (error) {
      if (error instanceof ApiClientError) {
        setError(error.message, "profile");
        setSaveResult({ kind: "error", message: error.message });
      } else {
        const message = "Failed to save profile";
        setError(message, "profile");
        setSaveResult({ kind: "error", message });
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="mx-auto w-full max-w-3xl">
      <Card>
        <CardHeader className="space-y-1.5">
          <CardTitle className="text-xl">{t(locale, "profile.title")}</CardTitle>
          <p className="text-sm text-muted-foreground">{t(locale, "profile.subtitle")}</p>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="grid gap-3 md:grid-cols-2">
            <div>
              <p className="mb-1 text-xs uppercase tracking-wide text-muted-foreground">{t(locale, "profile.name")}</p>
              <Input
                value={name}
                onChange={(e) => {
                  setName(e.target.value);
                  setSaveResult(null);
                }}
              />
            </div>
            <div>
              <p className="mb-1 text-xs uppercase tracking-wide text-muted-foreground">{t(locale, "profile.username")}</p>
              <Input
                value={username}
                onChange={(e) => {
                  setUsername(e.target.value);
                  setSaveResult(null);
                }}
              />
            </div>
          </div>
          <div>
            <p className="mb-1 text-xs uppercase tracking-wide text-muted-foreground">{t(locale, "profile.email")}</p>
            <Input
              type="email"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                setSaveResult(null);
              }}
            />
          </div>
          <div>
            <p className="mb-1 text-xs uppercase tracking-wide text-muted-foreground">{t(locale, "profile.password")}</p>
            <Input
              type="password"
              placeholder={t(locale, "profile.passwordHint")}
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                setSaveResult(null);
              }}
            />
          </div>
          <div>
            <p className="mb-1 text-xs uppercase tracking-wide text-muted-foreground">{t(locale, "profile.oldPassword")}</p>
            <Input
              type="password"
              placeholder={t(locale, "profile.oldPasswordHint")}
              value={oldPassword}
              onChange={(e) => {
                setOldPassword(e.target.value);
                setSaveResult(null);
              }}
            />
          </div>
          {saveResult ? (
            <p
              className={`rounded-lg border px-3 py-2 text-xs ${
                saveResult.kind === "success"
                  ? "border-emerald-300/35 bg-emerald-500/10 text-emerald-100"
                  : "border-rose-400/35 bg-rose-500/10 text-rose-200"
              }`}
            >
              {saveResult.message}
            </p>
          ) : null}
          <div className="flex items-center justify-between rounded-xl border border-border/80 bg-background/30 px-3 py-2 text-xs text-muted-foreground">
            <span className="rounded-full border border-border/70 px-2 py-0.5">{t(locale, "profile.role")}: {auth.user?.role ?? "USER"}</span>
            <span className="rounded-full border border-border/70 px-2 py-0.5">{t(locale, "profile.userId")}: {auth.user?.id ?? "-"}</span>
            <span className="rounded-full border border-border/70 px-2 py-0.5">Public API: {publicPing}</span>
          </div>
          <Button onClick={() => void handleSave()} disabled={saving || !hasChanges}>
            <Save className="h-4 w-4" />
            {saving ? t(locale, "profile.saving") : t(locale, "profile.save")}
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
