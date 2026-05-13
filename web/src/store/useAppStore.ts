import { create } from "zustand";
import { api, ApiRequestError } from "@/lib/api";
import type {
  Algo,
  ApiInfo,
  IsochroneResponse,
  LngLat,
  Profile,
  RouteResponse,
} from "@/lib/types";

/**
 * Single Zustand store. We picked Zustand over Context because:
 *  - one file
 *  - tiny API (set/get)
 *  - selective subscriptions out of the box
 *  - no provider wrapping needed
 *
 * All UI state + last route + last isochrone live here.
 */

export type Mode = "route" | "isochrone";

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
  setMode: (mode) => set({ mode, route: null, isochrone: null, compareResults: null }),

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
    if (mode === "isochrone" && from) void get().fetchIsochrone();
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
      // Run sequentially so the JVM warms each path independently and
      // we don't measure parallel contention. Each is fast anyway.
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
}));

function msg(e: unknown): string {
  if (e instanceof ApiRequestError) {
    return e.details.length ? `${e.message} (${e.details.join("; ")})` : e.message;
  }
  return e instanceof Error ? e.message : String(e);
}
