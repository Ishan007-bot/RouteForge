import { useEffect, useMemo } from "react";
import { X } from "lucide-react";
import { useAppStore } from "@/store/useAppStore";
import { algoLabel, algoShort } from "@/lib/theme";
import { formatInt, formatMeters, formatMs, formatSeconds } from "@/lib/format";
import { Button } from "./ui";
import type { Algo } from "@/lib/types";

export function ComparisonPanel() {
  const compareResults  = useAppStore((s) => s.compareResults);
  const compareLoading  = useAppStore((s) => s.compareLoading);
  const compareError    = useAppStore((s) => s.compareError);
  const clearComparison = useAppStore((s) => s.clearComparison);

  useEffect(() => {
    function onKey(e: KeyboardEvent) { if (e.key === "Escape") clearComparison(); }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [clearComparison]);

  const winnerByNodes = useMemo<Algo | null>(() => {
    if (!compareResults) return null;
    let best = compareResults[0];
    for (const r of compareResults) if (r.nodesSettled < best.nodesSettled) best = r;
    return best.algorithm;
  }, [compareResults]);

  if (!compareLoading && !compareResults && !compareError) return null;

  return (
    <div className="fixed inset-0 z-30 flex items-start justify-center bg-ink-950/85 px-6 py-12 backdrop-blur-[2px] animate-fade-in">
      <div className="panel relative w-full max-w-5xl animate-drop p-7">
        <button
          type="button"
          onClick={clearComparison}
          className="absolute right-4 top-4 text-paper-300 transition hover:text-brass-300"
          aria-label="Close"
        >
          <X className="h-4 w-4" />
        </button>

        <div className="flex items-start justify-between gap-6">
          <div>
            <div className="font-mono text-[10px] uppercase tracking-[0.18em] text-brass-400">
              Algorithm comparison
            </div>
            <h2
              className="font-display mt-1.5 text-[26px] leading-tight text-paper-50"
              style={{ fontVariationSettings: '"opsz" 144, "wght" 450, "SOFT" 30, "WONK" 0' }}
            >
              Same path, different cost to find it.
            </h2>
            <p className="mt-2 max-w-[58ch] text-[12.5px] leading-relaxed text-paper-300">
              All four were run on the same departure, arrival and profile.
              Distance and duration agree — that's correctness. Nodes settled is
              where the architectural choices show.
            </p>
          </div>
        </div>

        <div className="my-6 h-px bg-brass-700/25" />

        {compareError && (
          <div className="rounded-sm border border-oxblood-500/40 bg-oxblood-500/10 px-4 py-3 text-[12.5px] text-oxblood-300">
            {compareError}
          </div>
        )}

        {compareLoading && (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {[0, 1, 2, 3].map((i) => <div key={i} className="shimmer h-32 rounded-sm" />)}
          </div>
        )}

        {compareResults && (
          <>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              {compareResults.map((r) => {
                const isWinner = r.algorithm === winnerByNodes;
                return (
                  <div
                    key={r.algorithm}
                    className={[
                      "relative rounded-sm border bg-ink-800/80 p-4 transition",
                      isWinner ? "border-brass-500/80" : "border-brass-700/30",
                    ].join(" ")}
                  >
                    {isWinner && (
                      <span className="absolute -top-2 left-3 rounded-sm bg-ink-800 px-2 font-mono text-[9px] uppercase tracking-[0.18em] text-brass-300">
                        most efficient
                      </span>
                    )}
                    <div className="flex items-baseline justify-between gap-3">
                      <h3 className="font-sans text-[16px] font-semibold leading-none text-paper-50">
                        {algoLabel(r.algorithm)}
                      </h3>
                      <span className="font-mono text-[10px] uppercase tracking-[0.18em] text-paper-400">
                        {algoShort(r.algorithm)}
                      </span>
                    </div>

                    <div className="mt-3 h-px bg-brass-700/20" />

                    <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-3 text-[11.5px]">
                      <Row k="Distance" v={formatMeters(r.distanceMeters)} />
                      <Row k="Duration" v={formatSeconds(r.durationSeconds)} />
                      <Row k="Search"   v={formatMs(r.elapsedMillis)}     highlight={isWinner} />
                      <Row k="Settled"  v={formatInt(r.nodesSettled)}     highlight={isWinner} suffix="nodes" />
                    </dl>
                  </div>
                );
              })}
            </div>

            <p className="mt-6 text-[11.5px] leading-relaxed text-paper-300">
              <strong className="text-paper-100">A★</strong> trims Dijkstra by chasing the goal;
              {" "}<strong className="text-paper-100">bidirectional</strong> tightens it further by
              meeting in the middle; <strong className="text-paper-100">Contraction Hierarchies</strong>{" "}
              precomputes shortcuts so the query barely needs to think. All four return the same
              path — that's the engine telling the truth.
            </p>

            <div className="mt-5 flex justify-end">
              <Button onClick={clearComparison}>Close</Button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

function Row({ k, v, highlight, suffix }: { k: string; v: string; highlight?: boolean; suffix?: string }) {
  return (
    <div>
      <dt className="font-mono text-[10px] uppercase tracking-[0.18em] text-paper-400">{k}</dt>
      <dd className="num mt-0.5 flex items-baseline gap-1.5 font-mono text-[13px]">
        <span className={highlight ? "text-brass-300" : "text-paper-100"}>{v}</span>
        {suffix && <span className="text-[9px] uppercase tracking-[0.18em] text-paper-400">{suffix}</span>}
      </dd>
    </div>
  );
}
