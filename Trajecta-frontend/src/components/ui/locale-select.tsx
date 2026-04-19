import type { ChangeEvent } from "react";
import type { Locale } from "@/store/locale-store";
import { t } from "@/lib/i18n";

type LocaleSelectProps = {
  locale: Locale;
  onChange: (locale: Locale) => void;
  className?: string;
};

function parseLocale(value: string): Locale {
  if (value === "uk") {
    return value;
  }
  return "en";
}

export function LocaleSelect({ locale, onChange, className }: LocaleSelectProps) {
  const handleChange = (event: ChangeEvent<HTMLSelectElement>) => {
    onChange(parseLocale(event.target.value));
  };

  return (
    <label className={className ?? "flex h-9 items-center rounded-md border border-white/10 bg-[rgba(16,17,17,0.75)] px-3 text-sm text-muted-foreground"}>
      <select
        className="ui-select min-h-0 w-auto border-transparent bg-transparent py-1 pl-2 pr-6 text-sm text-foreground"
        value={locale}
        onChange={handleChange}
        aria-label={t(locale, "header.locale")}
      >
        <option value="en">EN</option>
        <option value="uk">UK</option>
      </select>
    </label>
  );
}
