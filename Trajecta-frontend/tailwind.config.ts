import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: ["class"],
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        card: "hsl(var(--card))",
        "card-foreground": "hsl(var(--card-foreground))",
        border: "hsl(var(--border))",
        muted: "hsl(var(--muted))",
        "muted-foreground": "hsl(var(--muted-foreground))",
        accent: "hsl(var(--accent))",
        ring: "hsl(var(--ring))"
      },
      boxShadow: {
        glow: "0 0 0 1px rgba(203, 207, 216, 0.18), 0 8px 24px rgba(0, 0, 0, 0.35)"
      },
      fontFamily: {
        sans: ["Inter", "Inter Fallback", "SF Pro Text", "Segoe UI", "sans-serif"],
        mono: ["GeistMono", "JetBrains Mono", "SFMono-Regular", "Roboto Mono", "Menlo", "Monaco", "monospace"]
      }
    }
  },
  plugins: []
};

export default config;
