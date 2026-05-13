import type { SimSnapshot } from "./types";

/**
 * A tiny auto-reconnecting WebSocket client for {@code /ws/sim}.
 *
 * The Vite dev proxy doesn't forward WebSockets by default, so we connect
 * directly to the API host. In production both are served from the same
 * origin and the same-origin URL still works.
 */
const WS_URL = (() => {
  // In dev, Vite is on :5173 and the API on :8080.
  // In prod, they share an origin.
  const { protocol, hostname, port } = window.location;
  const wsProto = protocol === "https:" ? "wss:" : "ws:";
  const isVite = port === "5173" || port === "3000";
  const host = isVite ? `${hostname}:8080` : `${hostname}${port ? ":" + port : ""}`;
  return `${wsProto}//${host}/ws/sim`;
})();

export interface SimSocket {
  close(): void;
}

export function openSimSocket(onSnapshot: (s: SimSnapshot) => void): SimSocket {
  let socket: WebSocket | null = null;
  let closed = false;
  let retryMs = 500;

  const connect = () => {
    socket = new WebSocket(WS_URL);
    socket.addEventListener("open", () => { retryMs = 500; });
    socket.addEventListener("message", (e) => {
      try {
        const snap = JSON.parse(e.data) as SimSnapshot;
        onSnapshot(snap);
      } catch { /* drop malformed */ }
    });
    socket.addEventListener("close", () => {
      if (closed) return;
      // Reconnect with linear back-off, capped.
      setTimeout(connect, retryMs);
      retryMs = Math.min(retryMs * 2, 5000);
    });
    socket.addEventListener("error", () => {
      try { socket?.close(); } catch { /* ignore */ }
    });
  };

  connect();

  return {
    close() {
      closed = true;
      try { socket?.close(); } catch { /* ignore */ }
    },
  };
}
