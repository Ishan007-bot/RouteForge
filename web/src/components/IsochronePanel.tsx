import { useAppStore } from "@/store/useAppStore";
import { formatInt, formatMs, formatSeconds } from "@/lib/format";
import { Skeleton } from "./ui";

const BUDGET_PRESETS = [300, 600, 900, 1500, 1800];

export function IsochronePanel() {
  const isoBudgetSeconds = useAppStore((s) => s.isoBudgetSeconds);
  const setIsoBudget     = useAppStore((s) => s.setIsoBudget);
  const isochrone        = useAppStore((s) => s.isochrone);
  const isoLoading       = useAppStore((s) => s.isoLoading);
  const isoError         = useAppStore((s) => s.isoError);
  const from             = useAppStore((s) => s.from);

  return (
    <div className="panel p-4">
      <div className="space-y-3">
        <div>
          <div className="font-mono text-[10px] uppercase tracking-[0.18em] text-paper-400">
            Reachable within
          </div>
          <div className="num mt-1 flex items-baseline gap-2">
            <span
              className="font-display font-light text-brass-300 leading-none"
              style={{
                fontSize: "30px",
                fontVariationSettings: '"opsz" 144, "wght" 350, "SOFT" 50, "WONK" 0',
              }}
            >
              {formatSeconds(isoBudgetSeconds).split(" ")[0]}
            </span>
            <span className="font-mono text-[10px] uppercase tracking-[0.18em] text-paper-300">
              {formatSeconds(isoBudgetSeconds).split(" ").slice(1).join(" ")}
            </span>
          </div>
        </div>

        <input
          type="range"
          min={60}
          max={3600}
          step={60}
          value={isoBudgetSeconds}
          onChange={(e) => setIsoBudget(Number(e.target.value))}
          className="w-full accent-brass-500"
        />

        <div className="flex flex-wrap gap-1.5">
          {BUDGET_PRESETS.map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => setIsoBudget(s)}
              className={[
                "rounded-sm border px-2.5 py-1 font-mono text-[10px] uppercase tracking-[0.18em] transition",
                s === isoBudgetSeconds
                  ? "border-brass-500 bg-brass-500/15 text-brass-200"
                  : "border-brass-700/35 bg-ink-700/40 text-paper-300 hover:border-brass-500/70 hover:text-paper-100",
              ].join(" ")}
            >
              {formatSeconds(s)}
            </button>
          ))}
        </div>

        {!from && (
          <div className="rounded-sm border border-brass-700/25 bg-ink-700/35 px-3 py-2 text-[11.5px] leading-relaxed text-paper-300">
            Drop a pin on the map to set the isochrone origin.
          </div>
        )}

        {isoError && (
          <div className="rounded-sm border border-oxblood-500/40 bg-oxblood-500/10 px-3 py-2 text-[11.5px] text-oxblood-300">
            {isoError}
          </div>
        )}

        {isoLoading && <Skeleton className="h-10" />}

        {isochrone && !isoLoading && (
          <div className="grid grid-cols-2 gap-3 border-t border-brass-700/25 pt-3">
            <Stat label="Reachable nodes" value={formatInt(isochrone.nodeCount)} />
            <Stat label="Search"          value={formatMs(isochrone.elapsedMillis)} />
          </div>
        )}
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="font-mono text-[10px] uppercase tracking-[0.18em] text-paper-400">
        {label}
      </div>
      <div className="num mt-0.5 font-mono text-[14px] text-paper-100">{value}</div>
    </div>
  );
}
