import { Button } from "@/components/ui/button";
import { BrandLogo } from "@/components/ui/brand-logo";
import { LocaleSelect } from "@/components/ui/locale-select";
import { useLocaleStore } from "@/store/locale-store";
import { t } from "@/lib/i18n";

type LandingScreenProps = {
  isAuthenticated: boolean;
  username?: string;
  onStart: () => void;
  onLogin: () => void;
};

export function LandingScreen({
  isAuthenticated,
  username,
  onStart,
  onLogin
}: LandingScreenProps) {
  const locale = useLocaleStore((s) => s.locale);
  const setLocale = useLocaleStore((s) => s.setLocale);

  return (
    <div className="relative flex min-h-[100dvh] flex-col overflow-hidden bg-background text-foreground">
      <div className="absolute inset-0 pointer-events-none bg-[radial-gradient(circle_at_16%_18%,rgba(255,99,99,0.17),transparent_30%),radial-gradient(circle_at_82%_14%,rgba(85,179,255,0.14),transparent_32%),radial-gradient(circle_at_50%_90%,rgba(215,201,175,0.08),transparent_35%)]" />

      <header className="relative z-10 mx-auto flex w-full max-w-7xl items-center justify-between px-4 py-4 md:px-8">
        <div className="flex items-center gap-3">
          <BrandLogo className="h-9 w-9 sm:h-10 sm:w-10" />
          <div className="leading-tight">
            <p className="text-sm font-medium text-foreground">Trajecta</p>
            <p className="hidden text-xs text-muted-foreground sm:block">{t(locale, "landing.caption")}</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <LocaleSelect locale={locale} onChange={setLocale} />

          {isAuthenticated ? (
            <div className="hidden h-9 items-center rounded-md border border-white/10 bg-[rgba(16,17,17,0.75)] px-3 text-sm text-muted-foreground sm:flex">
              {t(locale, "landing.hello")}: <span className="ml-1 font-semibold text-foreground">{username ?? "-"}</span>
            </div>
          ) : (
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="hidden h-9 px-3 text-sm sm:inline-flex"
              onClick={onLogin}
            >
              {t(locale, "landing.signIn")}
            </Button>
          )}
        </div>
      </header>

      <main className="relative z-10 mx-auto flex w-full max-w-7xl flex-1 items-center justify-center px-4 pb-8 pt-4 sm:pb-16 sm:pt-0 md:px-8">
        <section className="w-full max-w-3xl text-center">
          <div className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-3xl border border-white/10 bg-[rgba(16,17,17,0.75)] backdrop-blur-md animate-rise sm:mb-6 sm:h-20 sm:w-20">
            <BrandLogo className="h-10 w-10 sm:h-12 sm:w-12" />
          </div>

          <h1 className="landing-reveal landing-reveal-title mb-3 text-3xl font-semibold tracking-tight text-foreground sm:mb-4 sm:text-4xl md:text-6xl">
            {t(locale, "landing.title")}
          </h1>
          <p className="landing-reveal landing-reveal-text landing-reveal-delay mb-8 text-sm text-muted-foreground sm:mb-10 sm:text-base md:text-xl">
            {t(locale, "landing.slogan")}
          </p>

          <div className="landing-reveal landing-reveal-delay-2 flex items-center justify-center">
            <Button
              size="lg"
              className="landing-reveal-cta h-12 w-full max-w-xs bg-[hsl(var(--accent))] px-6 text-base font-semibold text-white hover:bg-[hsl(var(--accent))] sm:h-11 sm:w-auto sm:min-w-48 sm:text-sm"
              onClick={onStart}
            >
              {t(locale, "landing.workspace")}
            </Button>
          </div>
        </section>
      </main>

      <footer className="pointer-events-none relative z-10 mt-auto flex justify-center px-4 pb-3 sm:pb-6">
        <p className="landing-team-footer text-[11px] tracking-[0.14em] text-muted-foreground sm:text-xs">
          <span className="landing-team-typing">{t(locale, "landing.teamTagline")}</span>
        </p>
      </footer>
    </div>
  );
}
