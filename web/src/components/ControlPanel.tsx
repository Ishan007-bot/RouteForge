import { useEffect, type ReactNode } from "react";
import {
  Bike, Car, Footprints, GitCompareArrows, Navigation, Radar, RotateCcw, Activity,
} from "lucide-react";
import { useAppStore } from "@/store/useAppStore";
import type { Algo, Profile } from "@/lib/types";
import { Button, Segmented } from "./ui";
import { PinList } from "./PinList";
import { StatsCard } from "./StatsCard";
import { IsochronePanel } from "./IsochronePanel";
import { SimulationPanel } from "./SimulationPanel";

export function ControlPanel() {
  const mode          = useAppStore((s) => s.mode);
  const setMode       = useAppStore((s) => s.setMode);
  const profile       = useAppStore((s) => s.profile);
  const setProfile    = useAppStore((s) => s.setProfile);
  const algo          = useAppStore((s) => s.algo);
  const setAlgo       = useAppStore((s) => s.setAlgo);
  const clear         = useAppStore((s) => s.clear);
  const runComparison = useAppStore((s) => s.runComparison);
  const route         = useAppStore((s) => s.route);
  const from          = useAppStore((s) => s.from);
  const to            = useAppStore((s) => s.to);

  useEffect(() => {
    function onKey(e: KeyboardEvent) { if (e.key === "Escape") clear(); }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [clear]);

  return (
    <aside className="relative flex h-full w-[380px] shrink-0 flex-col gap-6 overflow-y-auto border-r border-brass-700/25 bg-ink-900/90 px-5 py-6">
      <Section title="Mode">
        <Segmented<"route" | "isochrone" | "simulator">
          value={mode}
          onChange={setMode}
          options={[
            { value: "route",     label: "Route",     icon: <Navigation className="h-3 w-3" /> },
            { value: "isochrone", label: "Isochrone", icon: <Radar      className="h-3 w-3" /> },
            { value: "simulator", label: "Simulator", icon: <Activity   className="h-3 w-3" /> },
          ]}
        />
      </Section>

      <Section title="Profile">
        <Segmented<Profile>
          value={profile}
          onChange={setProfile}
          options={[
            { value: "car",  label: "Car",  icon: <Car        className="h-3 w-3" /> },
            { value: "bike", label: "Bike", icon: <Bike       className="h-3 w-3" /> },
            { value: "foot", label: "Foot", icon: <Footprints className="h-3 w-3" /> },
          ]}
        />
      </Section>

      {mode === "route" && (
        <Section title="Algorithm" hint={algoBlurb(algo)}>
          <Segmented<Algo>
            value={algo}
            onChange={setAlgo}
            size="sm"
            options={[
              { value: "dijkstra",      label: "Dijkstra" },
              { value: "astar",         label: "A★" },
              { value: "bidirectional", label: "Bi-Dir" },
              { value: "ch",            label: "CH" },
            ]}
          />
        </Section>
      )}

      {(mode === "route" || mode === "isochrone" || mode === "simulator") && (
        <Section
          title={mode === "isochrone" ? "Origin" : "Endpoints"}
          action={
            <button
              type="button"
              onClick={clear}
              className="inline-flex items-center gap-1.5 font-mono text-[10px] uppercase tracking-[0.18em] text-paper-300 transition hover:text-brass-300"
            >
              <RotateCcw className="h-3 w-3" />
              Reset
            </button>
          }
        >
          <PinList />
        </Section>
      )}

      {mode === "route" && (
        <>
          <Section title="Result"><StatsCard /></Section>
          <Button
            variant="primary"
            onClick={runComparison}
            disabled={!from || !to || !route?.found}
            icon={<GitCompareArrows className="h-3.5 w-3.5" />}
          >
            Compare all algorithms
          </Button>
        </>
      )}

      {mode === "isochrone" && (
        <Section title="Isochrone"><IsochronePanel /></Section>
      )}

      {mode === "simulator" && (
        <Section title="Simulation"><SimulationPanel /></Section>
      )}
    </aside>
  );
}

function Section({
  title, hint, action, children,
}: {
  title: string;
  hint?: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section>
      <header className="mb-2.5 flex items-center justify-between gap-3">
        <h2 className="font-mono text-[10px] uppercase tracking-[0.22em] text-brass-400/85">
          {title}
        </h2>
        {action}
      </header>
      {children}
      {hint && (
        <p className="mt-2 text-[11.5px] leading-relaxed text-paper-300/80">
          {hint}
        </p>
      )}
    </section>
  );
}

function algoBlurb(a: Algo): string {
  switch (a) {
    case "dijkstra":
      return "Settles every node by distance. Slowest but a reliable yardstick.";
    case "astar":
      return "Dijkstra with a sense of direction — same answer, far fewer steps.";
    case "bidirectional":
      return "Two searches walking toward each other, meeting in the middle.";
    case "ch":
      return "Preprocessed hierarchy of shortcuts. Queries are nearly instant.";
  }
}
