import { ArrowDownUp, X } from "lucide-react";
import { useAppStore } from "@/store/useAppStore";
import { formatCoord } from "@/lib/format";

export function PinList() {
  const from    = useAppStore((s) => s.from);
  const to      = useAppStore((s) => s.to);
  const mode    = useAppStore((s) => s.mode);
  const swap    = useAppStore((s) => s.swap);
  const setFrom = useAppStore((s) => s.setFrom);
  const setTo   = useAppStore((s) => s.setTo);

  return (
    <div className="space-y-2">
      <PinRow
        glyph="A"
        accent="brass"
        label={mode === "isochrone" ? "Origin" : "Start"}
        latlon={from}
        onClear={() => setFrom(null)}
      />
      {mode === "route" && (
        <>
          <div className="flex justify-center">
            <button
              type="button"
              onClick={swap}
              disabled={!from || !to}
              title="Swap A and B"
              className="group inline-flex h-7 w-7 items-center justify-center rounded-sm border border-brass-700/40 text-brass-500/85 transition hover:border-brass-500 hover:text-brass-300 disabled:cursor-not-allowed disabled:opacity-30"
            >
              <ArrowDownUp className="h-3 w-3 transition group-hover:rotate-180" />
            </button>
          </div>
          <PinRow
            glyph="B"
            accent="oxblood"
            label="End"
            latlon={to}
            onClear={() => setTo(null)}
          />
        </>
      )}
    </div>
  );
}

function PinRow({
  glyph,
  accent,
  label,
  latlon,
  onClear,
}: {
  glyph: string;
  accent: "brass" | "oxblood";
  label: string;
  latlon: { lat: number; lon: number } | null;
  onClear: () => void;
}) {
  const set = accent === "brass"
    ? { ring: "border-brass-500/70 text-brass-200 bg-brass-700/15",  stripe: "bg-brass-500/80"   }
    : { ring: "border-oxblood-500/70 text-oxblood-200 bg-oxblood-700/20", stripe: "bg-oxblood-500/80" };

  return (
    <div className="group relative flex items-center gap-3 rounded-sm border border-brass-700/25 bg-ink-800/60 px-3 py-2.5 transition hover:border-brass-600/55">
      <span aria-hidden className={`absolute left-0 top-0 h-full w-px ${set.stripe}`} />

      <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-sm border ${set.ring} font-sans text-[13px] font-bold leading-none`}>
        {glyph}
      </div>

      <div className="min-w-0 flex-1">
        <div className="font-mono text-[9px] uppercase tracking-[0.18em] text-paper-400">
          {label}
        </div>
        <div className="num mt-0.5 truncate font-mono text-[12.5px] text-paper-100">
          {latlon
            ? `${formatCoord(latlon.lat)}, ${formatCoord(latlon.lon)}`
            : <span className="text-paper-400">unset</span>}
        </div>
      </div>

      {latlon && (
        <button
          type="button"
          onClick={onClear}
          aria-label={`Clear ${label}`}
          className="text-paper-400 transition hover:text-oxblood-300"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      )}
    </div>
  );
}
