type BrandLogoProps = {
  className?: string;
};

export function BrandLogo({ className }: BrandLogoProps) {
  return (
    <svg
      className={className}
      viewBox="0 0 64 64"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <rect x="6" y="6" width="52" height="52" rx="16" fill="url(#brand-bg)" />
      <path d="M16 41L28 23L38 33L48 19" stroke="#FF9A9A" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="48" cy="19" r="3.5" fill="#FF6363" />
      <path d="M18 49H46" stroke="#C0C0C0" strokeWidth="3" strokeLinecap="round" />
      <defs>
        <linearGradient id="brand-bg" x1="6" y1="8" x2="58" y2="58" gradientUnits="userSpaceOnUse">
          <stop stopColor="#2A2D33" />
          <stop offset="1" stopColor="#0F1114" />
        </linearGradient>
      </defs>
    </svg>
  );
}
