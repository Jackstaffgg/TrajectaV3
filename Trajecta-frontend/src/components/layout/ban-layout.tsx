import type { ReactNode } from "react";
import { ShieldAlert } from "lucide-react";

type BanLayoutProps = {
  children: ReactNode;
  menuTitle: string;
};

export function BanLayout({ children, menuTitle }: BanLayoutProps) {
  return (
    <div className="min-h-screen bg-background text-foreground p-4 md:p-6">
      <div className="mx-auto grid w-full max-w-6xl gap-4 md:grid-cols-[240px,1fr]">
        <aside className="surface-panel rounded-2xl border p-4">
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">TRAJECTA</p>
          <button
            type="button"
            className="flex w-full items-center gap-2 rounded-xl border border-white/10 bg-[rgba(16,17,17,0.75)] px-3 py-2 text-left text-sm text-foreground"
          >
            <ShieldAlert className="h-4 w-4" />
            <span>{menuTitle}</span>
          </button>
        </aside>

        <section className="surface-panel rounded-2xl border p-6">{children}</section>
      </div>
    </div>
  );
}


