import { create } from "zustand";
import { api, ApiRequestError } from "@/lib/api";
import { openSimSocket, type SimSocket } from "@/lib/simSocket";
import type {
  Algo,
  ApiInfo,
  IsochroneResponse,
  LngLat,
  Profile,
  RouteResponse,
  SimSnapshot,
} from "@/lib/types";

export type Mode = "route" | "isochrone" | "simulator";

interface AppState {
  // --- API status ---
  info: ApiInfo | null;
  infoError: string | null;
  loadInfo: () => Promise<void>;

  // --- Pin / mode ---
  mode: Mode;
  setMode: (m: Mode) => void;

  from: LngLat | null;
  to: LngLat | null;
  setFrom: (p: LngLat | null) => void;
  setTo: (p: LngLat | null) => void;
  swap: () => void;
  clear: () => void;

  // --- Routing settings ---
  profile: Profile;
  algo: Algo;
  setProfile: (p: Profile) => void;
  setAlgo: (a: Algo) => void;

  // --- Route result ---
  route: RouteResponse | null;
  routeLoading: boolean;
  routeError: string | null;
  fetchRoute: () => Promise<void>;

  // --- Isochrone result ---
  isoBudgetSeconds: number;
  isochrone: IsochroneResponse | null;
  isoLoading: boolean;
  isoError: string | null;
  setIsoBudget: (s: number) => void;
  fetchIsochrone: () => Promise<void>;

  // --- Algorithm comparison ---
  compareResults: RouteResponse[] | null;
  compareLoading: boolean;
  compareError: string | null;
  runComparison: () => Promise<void>;
  clearComparison: () => void;

  // --- Simulator ---
  simSnapshot: SimSnapshot | null;
  simSocket: SimSocket | null;
  simError: string | null;
  simClickAction: "spawn-from" | "spawn-to" | "close-edge" | "none";
  simFleetSize: number;
  setSimClickAction: (a: AppState["simClickAction"]) => void;
  setSimFleetSize: (n: number) => void;
  connectSim: () => void;
  disconnectSim: () => void;
  simPlay: () => Promise<void>;
  simPause: () => Promise<void>;
  simReset: () => Promise<void>;
  simSetSpeed: (x: number) => Promise<void>;
  simSpawnRandomFleet: () => Promise<void>;
  simSpawnPair: () => Promise<void>;
  simCloseAt: (lat: number, lon: number) => Promise<void>;
}

export const useAppStore = create<AppState>((set, get) => ({
  info: null,
  infoError: null,
  async loadInfo() {
    try {
      const info = await api.info();
      set({ info, infoError: null });
    } catch (e) {
      set({ infoError: msg(e) });
    }
  },

  mode: "route",
  setMode: (mode) => {
    set({ mode, route: null, isochrone: null, compareResults: null });
    if (mode === "simulator") get().connectSim();
    else                      get().disconnectSim();
  },

  from: null,
  to: null,
  setFrom: (from) => {
    set({ from, route: null, compareResults: null });
    if (from && get().to && get().mode === "route") void get().fetchRoute();
    if (from && get().mode === "isochrone") void get().fetchIsochrone();
  },
  setTo: (to) => {
    set({ to, route: null, compareResults: null });
    if (to && get().from && get().mode === "route") void get().fetchRoute();
  },
  swap: () => {
    const { from, to } = get();
    set({ from: to, to: from, route: null, compareResults: null });
    if (from && to && get().mode === "route") void get().fetchRoute();
  },
  clear: () =>
    set({
      from: null,
      to: null,
      route: null,
      routeError: null,
      isochrone: null,
      isoError: null,
      compareResults: null,
      compareError: null,
    }),

  profile: "car",
  algo: "astar",
  setProfile: (profile) => {
    set({ profile, compareResults: null });
    const { mode, from, to } = get();
    if (mode === "route" && from && to) void get().fetchRoute();
    if (mode === "isochrone" && from)   void get().fetchIsochrone();
  },
  setAlgo: (algo) => {
    set({ algo, compareResults: null });
    const { from, to } = get();
    if (from && to) void get().fetchRoute();
  },

  route: null,
  routeLoading: false,
  routeError: null,
  async fetchRoute() {
    const { from, to, profile, algo } = get();
    if (!from || !to) return;
    set({ routeLoading: true, routeError: null });
    try {
      const route = await api.route({
        fromLat: from.lat, fromLon: from.lon,
        toLat:   to.lat,   toLon:   to.lon,
        profile, algo,
      });
      set({ route, routeLoading: false });
    } catch (e) {
      set({ routeError: msg(e), routeLoading: false, route: null });
    }
  },

  isoBudgetSeconds: 600,
  isochrone: null,
  isoLoading: false,
  isoError: null,
  setIsoBudget: (s) => {
    set({ isoBudgetSeconds: s });
    if (get().mode === "isochrone" && get().from) void get().fetchIsochrone();
  },
  async fetchIsochrone() {
    const { from, isoBudgetSeconds, profile } = get();
    if (!from) return;
    set({ isoLoading: true, isoError: null });
    try {
      const iso = await api.isochrone({
        lat: from.lat, lon: from.lon,
        budgetSeconds: isoBudgetSeconds,
        profile,
      });
      set({ isochrone: iso, isoLoading: false });
    } catch (e) {
      set({ isoError: msg(e), isoLoading: false, isochrone: null });
    }
  },

  compareResults: null,
  compareLoading: false,
  compareError: null,
  async runComparison() {
    const { from, to, profile } = get();
    if (!from || !to) return;
    set({ compareLoading: true, compareError: null });
    try {
      const algos: Algo[] = ["dijkstra", "astar", "bidirectional", "ch"];
      const results: RouteResponse[] = [];
      for (const a of algos) {
        results.push(await api.route({
          fromLat: from.lat, fromLon: from.lon,
          toLat:   to.lat,   toLon:   to.lon,
          profile, algo: a,
        }));
      }
      set({ compareResults: results, compareLoading: false });
    } catch (e) {
      set({ compareError: msg(e), compareLoading: false, compareResults: null });
    }
  },
  clearComparison: () => set({ compareResults: null, compareError: null }),

  /* -------------------- Simulator -------------------- */

  simSnapshot: null,
  simSocket: null,
  simError: null,
  simClickAction: "none",
  simFleetSize: 30,
  setSimClickAction: (simClickAction) => set({ simClickAction }),
  setSimFleetSize: (simFleetSize) => set({ simFleetSize: Math.max(1, Math.min(500, simFleetSize)) }),

  connectSim: () => {
    if (get().simSocket) return;
    const sock = openSimSocket((snap) => set({ simSnapshot: snap }));
    set({ simSocket: sock, simError: null });
  },
  disconnectSim: () => {
    get().simSocket?.close();
    set({ simSocket: null, simSnapshot: null });
  },

  async simPlay()  { await guarded(() => api.simControl({ action: "play"  })); },
  async simPause() { await guarded(() => api.simControl({ action: "pause" })); },
  async simReset() {
    await guarded(() => api.simControl({ action: "reset" }));
    set({ simSnapshot: null });
  },
  async simSetSpeed(x) { await guarded(() => api.simControl({ speedMultiplier: x })); },

  async simSpawnRandomFleet() {
    const { info, simFleetSize, profile } = get();
    if (!info) return;
    // Pick random (from, to) pairs around the engine's known graph extent.
    // We don't have the bbox here, so pick around the current map center —
    // which the MapView attaches to window for this purpose.
    const ctr = window.__mapCenter ?? { lat: 47.154, lon: 9.5215 };
    const span = 0.04; // ~4km box
    const rand = () => (Math.random() * 2 - 1) * span;
    try {
      await Promise.all(Array.from({ length: simFleetSize }).map(() =>
        api.simSpawn({
          fromLat: ctr.lat + rand(), fromLon: ctr.lon + rand(),
          toLat:   ctr.lat + rand(), toLon:   ctr.lon + rand(),
          profile, count: 1,
        })
      ));
      set({ simError: null });
    } catch (e) { set({ simError: msg(e) }); }
  },

  async simSpawnPair() {
    const { from, to, profile } = get();
    if (!from || !to) return;
    try {
      await api.simSpawn({
        fromLat: from.lat, fromLon: from.lon,
        toLat:   to.lat,   toLon:   to.lon,
        profile, count: 1,
      });
      set({ simError: null });
    } catch (e) { set({ simError: msg(e) }); }
  },

  async simCloseAt(lat, lon) {
    try {
      await api.simEvent({ type: "close", lat, lon });
      set({ simError: null });
    } catch (e) { set({ simError: msg(e) }); }
  },
}));

function msg(e: unknown): string {
  if (e instanceof ApiRequestError) {
    return e.details.length ? `${e.message} (${e.details.join("; ")})` : e.message;
  }
  return e instanceof Error ? e.message : String(e);
}

async function guarded<T>(fn: () => Promise<T>) {
  try { await fn(); }
  catch (e) { useAppStore.setState({ simError: msg(e) }); }
}

declare global {
  // The MapView writes the current map center here on every moveend so the
  // store can read it without a circular dep.
  // eslint-disable-next-line no-var
  var __mapCenter: { lat: number; lon: number } | undefined;
}
