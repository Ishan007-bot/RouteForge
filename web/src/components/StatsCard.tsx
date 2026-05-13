import type { ReactNode } from "react";
import { useAppStore } from "@/store/useAppStore";
import { algoShort, profileColor, profileLabel } from "@/lib/theme";
import { formatInt, formatMeters, formatMs, formatSeconds } from "@/lib/format";
import { Badge, Skeleton } from "./ui";

export function StatsCard() {
  const route        = useAppStore((s) => s.route);
  const routeLoading = useAppStore((s) => s.routeLoading);
  const routeError   = useAppStore((s) => s.routeError);
  const from         = useAppStore((s) => s.from);
  const to           = useAppStore((s) => s.to);
  const profile      = useAppStore((s) => s.profile);
  const algo         = useAppStore((s) => s.algo);

  if (routeError) {
    return (
      <Frame>
        <div className="font-mono text-[10px] uppercase tracking-[0.18em] text-oxblood-400">
          Routing failed
        </div>
        <p className="mt-2 text-[12.5px] leading-relaxed text-oxblood-300/90">{routeError}</p>
      </Frame>
    );
  }

  if (!from || !to) {
    return (
      <Frame>
        <p className="text-[12.5px] leading-relaxed text-paper-300/85">
          Drop two pins on the map to plot a route.
        </p>
      </Frame>
    );
  }

  if (routeLoading) {
    return (
      <Frame>
        <div className="grid grid-cols-2 gap-3">
          <Skeleton className="h-14" />
          <Skeleton className="h-14" />
          <Skeleton className="h-14" />
          <Skeleton className="h-14" />
        </div>
      </Frame>
    );
  }

  if (!route?.found) {
    return (
      <Frame>
        <div className="font-mono text-[10px] uppercase tracking-[0.18em] text-oxblood-400/80">
          No route
        </div>
        <p className="mt-2 text-[12.5px] leading-relaxed text-paper-200">
          The two points aren't connected for this profile.
        </p>
      </Frame>
    );
  }

  const accent = profileColor(profile);

  return (
    <div className="panel animate-fade-up p-4">
      <div className="mb-3 flex items-baseline justify-between gap-3">
        <div className="text-[12px] text-paper-300">
          {profileLabel(profile)}{" "}
          <span className="text-paper-400">via</span>{" "}
          <span className="text-brass-300">{algoShort(algo)}</span>
        </div>
        <Badge tone="brass">found</Badge>
      </div>

      <div className="border-y border-brass-700/25 py-3.5">
        <div className="font-mono text-[10px] uppercase tracking-[0.18em] text-paper-400">
          Distance
        </div>
        <div className="num mt-1 flex items-baseline gap-2">
          <span
            className="font-display font-light leading-none"
            style={{
              color: accent,
              fontSize: "40px",
              fontVariationSettings: '"opsz" 144, "wght" 350, "SOFT" 50, "WONK" 0',
            }}
          >
            {formatMeters(route.distanceMeters).split(" ")[0]}
          </span>
          <span className="font-mono text-[10px] uppercase tracking-[0.18em] text-paper-300">
            {formatMeters(route.distanceMeters).split(" ")[1]}
          </span>
        </div>
      </div>

      <div className="mt-3 grid grid-cols-3 gap-3">
        <Metric label="Duration" value={formatSeconds(route.durationSeconds)} />
        <Metric label="Search"   value={formatMs(route.elapsedMillis)} />
        <Metric label="Settled"  value={formatInt(route.nodesSettled)} hint="nodes" />
      </div>
    </div>
  );
}

function Frame({ children }: { children: ReactNode }) {
  return <div className="panel p-4">{children}</div>;
}

function Metric({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div>
      <div className="font-mono text-[10px] uppercase tracking-[0.18em] text-paper-400">
        {label}
      </div>
      <div className="num mt-1 font-mono text-[15px] leading-tight text-paper-100">{value}</div>
      {hint && (
        <div className="font-mono text-[9px] uppercase tracking-[0.18em] text-paper-400/70">
          {hint}
        </div>
      )}
    </div>
  );
}
