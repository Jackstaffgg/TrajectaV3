import { useState } from "react";
import { Lock, UserPlus } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { localizeErrorMessage, t, tr } from "@/lib/i18n";
import { useFlightStore } from "@/store/flight-store";
import { useLocaleStore } from "@/store/locale-store";
import { ApiClientError, login, register } from "@/lib/api";

export function AuthScreen() {
  const [isRegister, setIsRegister] = useState(false);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const setAuthenticated = useFlightStore((s) => s.setAuthenticated);
  const locale = useLocaleStore((s) => s.locale);

  const requiredFieldsError = isRegister
    ? tr(locale, "auth.errors.required.register")
    : tr(locale, "auth.errors.required.login");

  const registerValidationError = tr(locale, "auth.errors.validation.register");

  async function handleAuth() {
    if (submitting) {
      return;
    }

    const normalized = {
      name: name.trim(),
      email: email.trim().toLowerCase(),
      username: username.trim(),
      password
    };

    if (!normalized.username || !normalized.password || (isRegister && (!normalized.name || !normalized.email))) {
      setError(requiredFieldsError);
      return;
    }

    if (isRegister) {
      const isValidName = normalized.name.length >= 4 && normalized.name.length <= 40;
      const isValidUsername = /^[a-zA-Z0-9_]{4,16}$/.test(normalized.username);
      const isValidEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalized.email);
      const isValidPassword = normalized.password.length >= 8;

      if (!isValidName || !isValidUsername || !isValidEmail || !isValidPassword) {
        setError(registerValidationError);
        return;
      }
    }

    setSubmitting(true);
    setError(null);
    try {
      const result = isRegister
        ? await register(normalized)
        : await login(normalized.username, normalized.password);
      setAuthenticated(result.token, result.user);
    } catch (e) {
      if (e instanceof ApiClientError) {
        if (isRegister && e.status === 400) {
          setError(`${localizeErrorMessage(e.message, locale)} ${registerValidationError}`);
        } else {
          setError(localizeErrorMessage(e.message, locale));
        }
      } else if (e instanceof Error) {
        setError(localizeErrorMessage(e.message, locale));
      } else {
        setError(tr(locale, "auth.errors.requestFailed"));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center p-4 md:p-6">
      <Card className="w-full max-w-md border-white/10 bg-card/95">
        <CardHeader className="space-y-2">
          <p className="inline-flex w-fit items-center rounded-full border border-white/15 bg-[hsl(var(--accent))]/20 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-foreground">
            {tr(locale, "auth.badge.secure")}
          </p>
          <CardTitle className="text-2xl font-bold text-foreground">
            {isRegister ? t(locale, "auth.title.register") : t(locale, "auth.title.login")}
          </CardTitle>
          <p className="text-sm leading-relaxed text-muted-foreground">
            {t(locale, "auth.subtitle")}
          </p>
        </CardHeader>
        <CardContent>
          <form className="space-y-3" onSubmit={(e) => {
            e.preventDefault();
            void handleAuth();
          }}>
          {isRegister ? (
            <Input
              type="text"
              value={name}
              onChange={(e) => {
                setError(null);
                setName(e.target.value);
              }}
              placeholder={tr(locale, "auth.placeholder.name")}
              autoComplete="name"
              aria-invalid={error ? true : undefined}
            />
          ) : null}
          {isRegister ? (
            <Input
              type="email"
              value={email}
              onChange={(e) => {
                setError(null);
                setEmail(e.target.value);
              }}
              placeholder="pilot@example.com"
              autoComplete="email"
              aria-invalid={error ? true : undefined}
            />
          ) : null}
          <Input
            type="text"
            value={username}
            onChange={(e) => {
              setError(null);
              setUsername(e.target.value);
            }}
            placeholder="pilot_01"
            autoComplete="username"
            aria-invalid={error ? true : undefined}
          />
          <Input
            type="password"
            value={password}
            onChange={(e) => {
              setError(null);
              setPassword(e.target.value);
            }}
            placeholder="********"
            autoComplete={isRegister ? "new-password" : "current-password"}
            aria-invalid={error ? true : undefined}
          />
          {error ? <p className="error-banner text-xs">{error}</p> : null}
          <Button className="w-full" type="submit" disabled={submitting}>
            {isRegister ? <UserPlus className="h-4 w-4" /> : <Lock className="h-4 w-4" />}
            {submitting ? t(locale, "auth.submit.wait") : isRegister ? t(locale, "auth.submit.register") : t(locale, "auth.submit.login")}
          </Button>
          <button
            type="button"
            className="w-full text-xs font-medium text-muted-foreground transition hover:text-foreground"
            onClick={() => {
              setError(null);
              setIsRegister((v) => !v);
            }}
          >
            {isRegister ? t(locale, "auth.switch.login") : t(locale, "auth.switch.register")}
          </button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
