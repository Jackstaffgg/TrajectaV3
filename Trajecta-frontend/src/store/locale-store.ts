import { create } from "zustand";

export type Locale = "en" | "uk";

type LocaleState = {
  locale: Locale;
  setLocale: (locale: Locale) => void;
};

const LOCALE_STORAGE_KEY = "trajecta.locale";

function loadLocale(): Locale {
  if (typeof window === "undefined") {
    return "en";
  }

  const raw = window.localStorage.getItem(LOCALE_STORAGE_KEY);
  return raw === "en" || raw === "uk" ? raw : "en";
}

function persistLocale(locale: Locale) {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(LOCALE_STORAGE_KEY, locale);
}

export const useLocaleStore = create<LocaleState>((set) => ({
  locale: loadLocale(),
  setLocale: (locale) => {
    persistLocale(locale);
    set({ locale });
  }
}));


