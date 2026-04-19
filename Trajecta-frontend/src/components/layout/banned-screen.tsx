import { ShieldAlert } from "lucide-react";
import { Button } from "@/components/ui/button";
import { BanLayout } from "@/components/layout/ban-layout";
import { formatDateByLocale, t } from "@/lib/i18n";
import { useLocaleStore } from "@/store/locale-store";
import type { UserBannedSocketPayload } from "@/types/flight";

type BannedScreenProps = {
  payload: UserBannedSocketPayload;
  onLogout: () => void;
};

export function BannedScreen({ payload, onLogout }: BannedScreenProps) {
  const locale = useLocaleStore((s) => s.locale);

  return (
    <BanLayout menuTitle={t(locale, "banned.title")}>
      <div className="mb-4 flex items-center gap-3">
        <ShieldAlert className="h-7 w-7 text-[hsl(var(--accent))]" />
        <h1 className="text-2xl font-semibold text-foreground">{t(locale, "banned.title")}</h1>
      </div>

      <p className="mb-4 text-sm text-muted-foreground">
        {t(locale, "banned.subtitle")}
      </p>

      <div className="space-y-3 rounded-xl border border-border/70 bg-background/40 p-4 text-sm">
        <p><span className="text-muted-foreground">{t(locale, "banned.reason")}:</span> <span className="text-foreground">{payload.reason}</span></p>
        <p><span className="text-muted-foreground">{t(locale, "banned.by")}:</span> <span className="text-foreground">{payload.punishedByName}</span></p>
        <p><span className="text-muted-foreground">{t(locale, "banned.until")}:</span> <span className="text-foreground">{payload.expiredAt ? formatDateByLocale(payload.expiredAt, locale) : t(locale, "common.permanent")}</span></p>
      </div>

      <div className="mt-5 flex justify-end">
        <Button onClick={onLogout}>{t(locale, "banned.signOut")}</Button>
      </div>
    </BanLayout>
  );
}


