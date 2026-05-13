import { clsx } from "clsx";
import type { ButtonHTMLAttributes, HTMLAttributes, ReactNode } from "react";
import { RegistrationMarks } from "./decorations";

/* ---------- Button ----------
 *
 * No rounded pills. Sharp 2px corners with a hairline brass edge, like
 * a metal nameplate. Primary buttons fill with brass; secondaries are
 * ink with brass outline; ghosts are bare with brass underline on hover.
 */
interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "ghost" | "danger";
  size?: "sm" | "md";
  icon?: ReactNode;
}

export function Button({
  variant = "secondary",
  size = "md",
  icon,
  className,
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      {...rest}
      className={clsx(
        "group/btn relative inline-flex items-center justify-center gap-2 font-sans transition-colors",
        "focus:outline-none focus-visible:ring-1 focus-visible:ring-brass-500",
        "disabled:cursor-not-allowed disabled:opacity-40",
        size === "sm" ? "px-2.5 py-1.5 text-[11px]" : "px-3.5 py-2 text-[12px]",

        // Editorial: nearly square corners (2px), tracked uppercase.
        "rounded-[2px] uppercase tracking-[0.16em] font-medium",

        variant === "primary" &&
          "border border-brass-500 bg-brass-500 text-ink-900 hover:bg-brass-400 hover:border-brass-400 shadow-brass",
        variant === "secondary" &&
          "border border-brass-700/55 bg-ink-700/40 text-paper-100 hover:border-brass-500/80 hover:bg-ink-700/70",
        variant === "ghost" &&
          "border border-transparent text-paper-200 hover:text-brass-300 hover:border-brass-700/35",
        variant === "danger" &&
          "border border-oxblood-500/60 bg-oxblood-500/15 text-oxblood-300 hover:bg-oxblood-500/25",

        className,
      )}
    >
      {icon && <span className="shrink-0 -ml-0.5">{icon}</span>}
      {children}
    </button>
  );
}

/* ---------- Card ----------
 *
 * The atlas panel: deep ink, hairline brass border, top highlight glow,
 * registration marks in the four corners.
 */
interface CardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
  marks?: boolean;
}

export function Card({ children, className, marks = true, ...rest }: CardProps) {
  return (
    <div
      {...rest}
      className={clsx(
        "panel relative p-4 pt-4",
        className,
      )}
    >
      {marks && <RegistrationMarks />}
      <div className="relative z-[1]">{children}</div>
    </div>
  );
}

/* ---------- Badge ---------- */
type BadgeTone = "default" | "brass" | "oxblood" | "lake" | "muted";

export function Badge({
  tone = "default",
  children,
  className,
}: {
  tone?: BadgeTone;
  children: ReactNode;
  className?: string;
}) {
  return (
    <span
      className={clsx(
        "inline-flex items-center gap-1.5 border px-2 py-0.5 font-mono",
        "text-[10px] uppercase tracking-atlas",
        "rounded-[2px]",
        tone === "default" && "border-brass-700/45 bg-ink-700/55 text-paper-200/85",
        tone === "brass"   && "border-brass-500/65   bg-brass-500/10   text-brass-300",
        tone === "oxblood" && "border-oxblood-500/55 bg-oxblood-500/10 text-oxblood-300",
        tone === "lake"    && "border-lake-500/55    bg-lake-500/10    text-lake-300",
        tone === "muted"   && "border-brass-700/25   bg-ink-700/35     text-paper-300/75",
        className,
      )}
    >
      {children}
    </span>
  );
}

/* ---------- Segmented control ----------
 *
 * Editorial tabbed strip with a brass underline on the active item.
 * Sharp corners, no pills.
 */
export function Segmented<T extends string>({
  options,
  value,
  onChange,
  size = "md",
}: {
  options: { value: T; label: string; icon?: ReactNode }[];
  value: T;
  onChange: (v: T) => void;
  size?: "sm" | "md";
}) {
  return (
    <div className="flex w-full border border-brass-700/35 bg-ink-700/40 rounded-[2px]">
      {options.map((opt, i) => {
        const active = opt.value === value;
        return (
          <button
            key={opt.value}
            type="button"
            onClick={() => onChange(opt.value)}
            className={clsx(
              "group relative flex flex-1 items-center justify-center gap-1.5 transition-colors",
              size === "sm" ? "px-1.5 py-1 text-[10px]" : "px-2.5 py-1.5 text-[11px]",
              "uppercase tracking-[0.16em] font-medium",
              i > 0 && "border-l border-brass-700/30",
              active
                ? "text-brass-300 bg-ink-800"
                : "text-paper-300 hover:text-paper-100 hover:bg-ink-700/40",
            )}
          >
            {opt.icon && <span className="opacity-90">{opt.icon}</span>}
            <span>{opt.label}</span>
            {active && (
              <span
                aria-hidden
                className="pointer-events-none absolute inset-x-1.5 -bottom-px h-px bg-brass-500"
              />
            )}
          </button>
        );
      })}
    </div>
  );
}

/* ---------- Skeleton ---------- */
export function Skeleton({ className }: { className?: string }) {
  return <div className={clsx("shimmer rounded-[2px]", className)} />;
}
