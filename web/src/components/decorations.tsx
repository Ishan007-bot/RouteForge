import { clsx } from "clsx";

/**
 * Hairline crosshairs at the four corners of a panel — the registration marks
 * a print shop uses to align color plates. Visually anchors the panel.
 */
export function RegistrationMarks({ className }: { className?: string }) {
  return (
    <div
      aria-hidden
      className={clsx(
        "pointer-events-none absolute inset-0 select-none text-brass-500/55",
        className,
      )}
    >
      <Crosshair className="absolute left-1.5 top-1.5" />
      <Crosshair className="absolute right-1.5 top-1.5" />
      <Crosshair className="absolute bottom-1.5 left-1.5" />
      <Crosshair className="absolute right-1.5 bottom-1.5" />
    </div>
  );
}

function Crosshair({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      width="10"
      height="10"
      viewBox="0 0 10 10"
      fill="none"
      stroke="currentColor"
      strokeWidth="0.8"
    >
      <line x1="0" y1="5" x2="10" y2="5" />
      <line x1="5" y1="0" x2="5" y2="10" />
      <circle cx="5" cy="5" r="1.5" />
    </svg>
  );
}

/** Decorative compass rose used in the header and on empty states. */
export function CompassRose({ className }: { className?: string }) {
  return (
    <svg
      className={clsx("text-brass-500", className)}
      viewBox="0 0 40 40"
      fill="none"
      aria-hidden
    >
      <circle cx="20" cy="20" r="17" stroke="currentColor" strokeWidth="0.6" opacity="0.55" />
      <circle cx="20" cy="20" r="12" stroke="currentColor" strokeWidth="0.4" opacity="0.4" />
      <circle cx="20" cy="20" r="6"  stroke="currentColor" strokeWidth="0.3" opacity="0.25" />
      {/* Cardinal rays */}
      <path d="M20 3.5 L21.4 20 L18.6 20 Z" fill="currentColor" />
      <path d="M20 36.5 L21.4 20 L18.6 20 Z" fill="currentColor" opacity="0.35" />
      <path d="M36.5 20 L20 21.4 L20 18.6 Z" fill="currentColor" opacity="0.7" />
      <path d="M3.5 20 L20 21.4 L20 18.6 Z" fill="currentColor" opacity="0.7" />
      {/* Inter-cardinal hairlines */}
      <line x1="9" y1="9" x2="31" y2="31" stroke="currentColor" strokeWidth="0.3" opacity="0.35" />
      <line x1="31" y1="9" x2="9" y2="31" stroke="currentColor" strokeWidth="0.3" opacity="0.35" />
      {/* N glyph */}
      <text
        x="20" y="6.5" textAnchor="middle"
        fontSize="3.4" fontFamily="JetBrains Mono, monospace"
        fontWeight="600" letterSpacing="0.18em"
        fill="currentColor"
      >N</text>
      {/* Center pivot */}
      <circle cx="20" cy="20" r="1.2" fill="#0a0c0f" stroke="currentColor" strokeWidth="0.5" />
    </svg>
  );
}

/** A horizontal hairline rule with an inline glyph at center. */
export function RuledDivider({ children, className }: { children?: string; className?: string }) {
  return (
    <div className={clsx("relative flex items-center", className)}>
      <span className="flex-1 border-t border-brass-700/35" />
      {children && (
        <span className="px-2 font-mono text-[10px] uppercase tracking-atlas text-brass-500/75">
          {children}
        </span>
      )}
      <span className="flex-1 border-t border-brass-700/35" />
    </div>
  );
}
