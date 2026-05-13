import type {
  ApiError,
  ApiInfo,
  IsochroneRequest,
  IsochroneResponse,
  RouteRequest,
  RouteResponse,
  SimControlRequest,
  SimEventRequest,
  SimSpawnRequest,
} from "./types";

/**
 * Minimal typed wrapper around fetch. Vite proxies /api/* to the Spring Boot
 * backend in development, so we hit the same origin from the browser.
 */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...init,
  });
  if (!res.ok) {
    let body: ApiError | undefined;
    try {
      body = (await res.json()) as ApiError;
    } catch {
      /* not JSON */
    }
    const message = body?.message ?? `${res.status} ${res.statusText}`;
    throw new ApiRequestError(message, res.status, body?.details ?? []);
  }
  return (await res.json()) as T;
}

export class ApiRequestError extends Error {
  readonly status: number;
  readonly details: string[];
  constructor(message: string, status: number, details: string[]) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

export const api = {
  info: () => request<ApiInfo>("/api/info"),

  route: (req: RouteRequest) =>
    request<RouteResponse>("/api/route", {
      method: "POST",
      body: JSON.stringify(req),
    }),

  isochrone: (req: IsochroneRequest) =>
    request<IsochroneResponse>("/api/isochrone", {
      method: "POST",
      body: JSON.stringify(req),
    }),

  simControl: (req: SimControlRequest) =>
    request<unknown>("/api/sim/control", {
      method: "POST",
      body: JSON.stringify(req),
    }),

  simSpawn: (req: SimSpawnRequest) =>
    request<{ firstId: number; count: number }>("/api/sim/spawn", {
      method: "POST",
      body: JSON.stringify(req),
    }),

  simEvent: (req: SimEventRequest) =>
    request<{ edgeId: number; type: string }>("/api/sim/event", {
      method: "POST",
      body: JSON.stringify(req),
    }),
};
