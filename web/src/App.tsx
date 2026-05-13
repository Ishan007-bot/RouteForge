import { useEffect } from "react";
import { useAppStore } from "@/store/useAppStore";
import { Header } from "./components/Header";
import { ControlPanel } from "./components/ControlPanel";
import { MapView } from "./components/MapView";
import { ComparisonPanel } from "./components/ComparisonPanel";

export function App() {
  const loadInfo  = useAppStore((s) => s.loadInfo);
  const infoError = useAppStore((s) => s.infoError);

  useEffect(() => {
    void loadInfo();
  }, [loadInfo]);

  return (
    <div className="flex h-full w-full flex-col">
      <Header />

      {infoError && (
        <div className="shrink-0 border-b border-oxblood-500/40 bg-oxblood-500/10 px-4 py-1.5 text-center font-mono text-[11px] text-oxblood-200">
          <span className="text-oxblood-300">Engine offline</span>
          <span className="mx-2 text-oxblood-400/60">·</span>
          <span className="text-oxblood-200/85">start it with </span>
          <code className="rounded-sm border border-oxblood-500/30 bg-ink-800 px-1.5 py-px text-[10.5px] text-oxblood-100">
            mvnw -pl api spring-boot:run
          </code>
        </div>
      )}

      <main className="flex flex-1 overflow-hidden">
        <ControlPanel />
        <section className="relative flex-1">
          <MapView />
        </section>
      </main>

      <ComparisonPanel />
    </div>
  );
}
