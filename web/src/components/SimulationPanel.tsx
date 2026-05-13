import { useEffect } from "react";
import { Pause, Play, RotateCcw, Car, Users, Ban, MousePointerClick } from "lucide-react";
import { useAppStore } from "@/store/useAppStore";
import { Button } from "./ui";

const SPEED_OPTIONS = [0.5, 1, 2, 4];

export function SimulationPanel() {
  const snap            = useAppStore((s) => s.simSnapshot);
  const simError        = useAppStore((s) => s.simError);
  const clickAction     = useAppStore((s) => s.simClickAction);
  const setClickAction  = useAppStore((s) => s.setSimClickAction);
  const fleetSize       = useAppStore((s) => s.simFleetSize);
  const setFleetSize    = useAppStore((s) => s.setSimFleetSize);
  const simPlay         = useAppStore((s) => s.simPlay);
  const simPause        = useAppStore((s) => s.simPause);
  const simReset        = useAppStore((s) => s.simReset);
  const simSetSpeed     = useAppStore((s) => s.simSetSpeed);
  const simSpawnFleet   = useAppStore((s) => s.simSpawnRandomFleet);
  const simSpawnPair    = useAppStore((s) => s.simSpawnPair);
  const connectSim      = useAppStore((s) => s.connectSim);
  const from            = useAppStore((s) => s.from);
  const to              = useAppStore((s) => s.to);

  useEffect(() => { connectSim(); }, [connectSim]);

  const running = !!snap?.running;
  const speed   = snap?.speedMultiplier ?? 1;

  return (
    <div className="space-y-4">
      {/* Transport — play / pause / reset */}
      <div className="panel p-4">
        <div className="flex items-center gap-2">
          {running ? (
            <Button variant="secondary" onClick={simPause} icon={<Pause className="h-3.5 w-3.5" />} className="flex-1">
              Pause
            </Button>
          ) : (
            <Button variant="primary" onClick={simPlay} icon={<Play className="h-3.5 w-3.5" />} className="flex-1">
              Play
            </Button>
          )}
          <Button variant="ghost" onClick={simReset} icon={<RotateCcw className="h-3.5 w-3.5" />}>
            Reset
          </Button>
        </div>

        <div className="mt-4">
          <div className="mb-1.5 flex items-center justify-between">
            <span className="font-mono text-[10px] uppercase tracking-[0.22em] text-brass-400/85">Speed</span>
            <span className="num font-mono text-[12px] text-paper-100">{speed.toFixed(1)}×</span>
          </div>
          <div className="flex gap-1.5">
            {SPEED_OPTIONS.map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => simSetSpeed(s)}
                className={[
                  "flex-1 rounded-sm border py-1.5 font-mono text-[11px] uppercase tracking-[0.16em] transition",
                  Math.abs(speed - s) < 0.05
                    ? "border-brass-500 bg-brass-500/15 text-brass-200"
                    : "border-brass-700/35 bg-ink-700/40 text-paper-300 hover:border-brass-500/70 hover:text-paper-100",
                ].join(" ")}
              >
                {s}×
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Spawn */}
      <div className="panel p-4">
        <h3 className="mb-3 font-mono text-[10px] uppercase tracking-[0.22em] text-brass-400/85">
          Spawn vehicles
        </h3>

        <div className="mb-3">
          <div className="mb-1 flex items-center justify-between">
            <span className="font-mono text-[10px] uppercase tracking-[0.18em] text-paper-400">Fleet size</span>
            <span className="num font-mono text-[12px] text-paper-100">{fleetSize}</span>
          </div>
          <input
            type="range"
            min={1} max={200} step={1}
            value={fleetSize}
            onChange={(e) => setFleetSize(Number(e.target.value))}
            className="w-full accent-brass-500"
          />
        </div>

        <div className="flex gap-2">
          <Button variant="secondary" onClick={simSpawnFleet} icon={<Users className="h-3.5 w-3.5" />} className="flex-1">
            Random fleet
          </Button>
          <Button
            variant="secondary"
            onClick={simSpawnPair}
            disabled={!from || !to}
            icon={<Car className="h-3.5 w-3.5" />}
            className="flex-1"
          >
            From A → B
          </Button>
        </div>
        {(!from || !to) && (
          <p className="mt-2 font-mono text-[10px] text-paper-400">
            Drop A &amp; B pins (or switch to Route first) to enable A → B spawn.
          </p>
        )}
      </div>

      {/* Click-to-edit */}
      <div className="panel p-4">
        <h3 className="mb-2 font-mono text-[10px] uppercase tracking-[0.22em] text-brass-400/85">
          Map click action
        </h3>
        <div className="grid grid-cols-2 gap-2">
          <ClickModeButton
            active={clickAction === "none"}
            onClick={() => setClickAction("none")}
            icon={<MousePointerClick className="h-3.5 w-3.5" />}
            label="None"
          />
          <ClickModeButton
            active={clickAction === "close-edge"}
            onClick={() => setClickAction("close-edge")}
            icon={<Ban className="h-3.5 w-3.5" />}
            label="Close road"
            danger
          />
        </div>
        <p className="mt-2 text-[11.5px] leading-relaxed text-paper-300/80">
          Pick &ldquo;Close road&rdquo;, then click any street on the map to shut it. Vehicles using
          that road will reroute on the next tick.
        </p>
      </div>

      {/* Live counters */}
      <div className="panel p-4">
        <h3 className="mb-3 font-mono text-[10px] uppercase tracking-[0.22em] text-brass-400/85">
          Live state
        </h3>
        <div className="grid grid-cols-2 gap-x-4 gap-y-3">
          <Stat label="Tick"       value={String(snap?.tick ?? 0)} />
          <Stat label="Sim time"   value={`${(snap?.simSeconds ?? 0).toFixed(1)} s`} />
          <Stat label="Active"     value={String(snap?.activeVehicles ?? 0)} />
          <Stat label="Arrived"    value={String(snap?.arrivedVehicles ?? 0)} />
          <Stat label="Stuck"      value={String(snap?.stuckVehicles ?? 0)} muted />
          <Stat label="Congested"  value={String(snap?.congestedEdges ?? 0)} />
          <Stat label="Closures"   value={String(snap?.closedEdges ?? 0)} />
          <Stat label="Status"     value={running ? "running" : "paused"} />
        </div>
      </div>

      {simError && (
        <div className="rounded-sm border border-oxblood-500/40 bg-oxblood-500/10 px-3 py-2 text-[11.5px] text-oxblood-300">
          {simError}
        </div>
      )}
    </div>
  );
}

function Stat({ label, value, muted }: { label: string; value: string; muted?: boolean }) {
  return (
    <div>
      <div className="font-mono text-[9px] uppercase tracking-[0.18em] text-paper-400">{label}</div>
      <div className={`num mt-0.5 font-mono text-[14px] ${muted ? "text-paper-300" : "text-paper-100"}`}>
        {value}
      </div>
    </div>
  );
}

function ClickModeButton({
  active, onClick, icon, label, danger,
}: {
  active: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  label: string;
  danger?: boolean;
}) {
  const activeCls = danger
    ? "border-oxblood-500 bg-oxblood-500/15 text-oxblood-200"
    : "border-brass-500 bg-brass-500/15 text-brass-200";
  return (
    <button
      type="button"
      onClick={onClick}
      className={[
        "flex items-center justify-center gap-2 rounded-sm border py-2 font-mono text-[10px] uppercase tracking-[0.16em] transition",
        active ? activeCls : "border-brass-700/35 bg-ink-700/40 text-paper-300 hover:border-brass-500/70 hover:text-paper-100",
      ].join(" ")}
    >
      {icon}
      {label}
    </button>
  );
}
