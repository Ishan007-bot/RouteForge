import { Github } from "lucide-react";
import { useAppStore } from "@/store/useAppStore";

export function Header() {
  const info      = useAppStore((s) => s.info);
  const infoError = useAppStore((s) => s.infoError);

  const online = !!info && !infoError;

  return (
    <header className="relative z-20 flex h-14 shrink-0 items-center justify-between border-b border-brass-700/25 bg-ink-900/95 px-6">
      <div className="flex items-center gap-3">
        <div
          className="flex h-7 w-7 items-center justify-center border border-brass-600/60 bg-ink-800 font-display text-[16px] italic leading-none text-brass-300"
          style={{ fontVariationSettings: '"opsz" 144, "wght" 500, "WONK" 1' }}
          aria-hidden
        >
          R
        </div>
        <h1
          className="font-display text-[22px] leading-none text-paper-50"
          style={{ fontVariationSettings: '"opsz" 144, "wght" 450, "SOFT" 30, "WONK" 0' }}
        >
          Route<span className="text-brass-300">Forge</span>
        </h1>
        <span className="ml-1 hidden font-mono text-[10px] uppercase tracking-[0.18em] text-paper-400 sm:inline">
          graph routing playground
        </span>
      </div>

      <div className="flex items-center gap-5">
        <span className="flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.18em]">
          <span
            className={[
              "h-1.5 w-1.5 rounded-full",
              online ? "bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.6)]" : "bg-oxblood-400",
            ].join(" ")}
          />
          <span className={online ? "text-paper-200" : "text-oxblood-300"}>
            {online ? "engine online" : "engine offline"}
          </span>
        </span>

        {info && (
          <div className="hidden items-center gap-4 md:flex">
            <Datum label="nodes" value={info.graph.nodes.toLocaleString()} />
            <Datum label="edges" value={info.graph.edges.toLocaleString()} />
            <Datum label="algos" value={String(info.algorithms.length)} />
          </div>
        )}

        <a
          href="https://github.com/Ishan007-bot/RouteForge"
          target="_blank"
          rel="noreferrer"
          aria-label="Source on GitHub"
          className="inline-flex h-7 w-7 items-center justify-center rounded-sm border border-brass-700/40 text-paper-200 transition hover:border-brass-500 hover:text-brass-300"
        >
          <Github className="h-3.5 w-3.5" />
        </a>
      </div>
    </header>
  );
}

function Datum({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline gap-1.5 leading-none">
      <span className="num font-mono text-[13px] text-paper-100">{value}</span>
      <span className="font-mono text-[9px] uppercase tracking-[0.18em] text-paper-400">{label}</span>
    </div>
  );
}
