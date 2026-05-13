// Mirror the API contracts from api/dto/*. Kept in one file because there
// are just a handful — easy to scan, easy to keep in sync.

export type Profile = "car" | "bike" | "foot";
export type Algo = "dijkstra" | "astar" | "bidirectional" | "ch";

export interface RouteRequest {
  fromLat: number;
  fromLon: number;
  toLat: number;
  toLon: number;
  profile: Profile;
  algo: Algo;
}

export interface RouteResponse {
  found: boolean;
  algorithm: Algo;
  profile: Profile;
  distanceMeters: number;
  durationSeconds: number;
  elapsedMillis: number;
  nodesSettled: number;
  /** [lat, lon] pairs */
  geometry: [number, number][];
}

export interface IsochroneRequest {
  lat: number;
  lon: number;
  budgetSeconds: number;
  profile: Profile;
}

export interface IsochroneResponse {
  profile: Profile;
  budgetSeconds: number;
  elapsedMillis: number;
  nodeCount: number;
  /** [lat, lon, secondsFromOrigin] triples */
  points: [number, number, number][];
}

export interface ApiInfo {
  service: string;
  version: string;
  graph: { nodes: number; edges: number };
  profiles: Profile[];
  algorithms: Algo[];
}

export interface ApiError {
  code: string;
  message: string;
  details?: string[];
  timestamp?: string;
}

/** A simple { lat, lon } pair we use for pins. */
export interface LngLat {
  lat: number;
  lon: number;
}

/* -------------------- Simulator -------------------- */

export type VehicleStatus = "active" | "arrived" | "stuck";

export interface VehicleSnap {
  id: number;
  lat: number;
  lon: number;
  heading: number;
  status: VehicleStatus;
  profile: Profile;
  progressFraction: number;
}

export interface EdgeSnap {
  edgeId: number;
  fromLat: number;
  fromLon: number;
  toLat: number;
  toLon: number;
}

export interface SimSnapshot {
  tick: number;
  simSeconds: number;
  activeVehicles: number;
  arrivedVehicles: number;
  stuckVehicles: number;
  congestedEdges: number;
  closedEdges: number;
  speedMultiplier: number;
  running: boolean;
  vehicles: VehicleSnap[];
  closedEdgeGeoms: EdgeSnap[];
}

export interface SimSpawnRequest {
  fromLat: number; fromLon: number;
  toLat:   number; toLon:   number;
  profile: Profile;
  count:   number;
}

export interface SimEventRequest {
  type: "close" | "open";
  lat:  number;
  lon:  number;
  edgeId?: number;
}

export interface SimControlRequest {
  action?: "play" | "pause" | "reset";
  speedMultiplier?: number;
}
