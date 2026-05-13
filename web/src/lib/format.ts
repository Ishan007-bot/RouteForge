// Small formatters used in stats and panel labels.

export function formatMeters(m: number): string {
  if (m < 1000) return `${Math.round(m)} m`;
  return `${(m / 1000).toFixed(m < 10_000 ? 2 : 1)} km`;
}

export function formatSeconds(s: number): string {
  if (!Number.isFinite(s) || s < 0) return "—";
  const total = Math.round(s);
  if (total < 60) return `${total} s`;
  const m = Math.floor(total / 60);
  const sec = total % 60;
  if (m < 60) return sec === 0 ? `${m} min` : `${m}m ${sec.toString().padStart(2, "0")}s`;
  const h = Math.floor(m / 60);
  const min = m % 60;
  return `${h}h ${min.toString().padStart(2, "0")}m`;
}

export function formatMs(ms: number): string {
  if (ms < 1) return "< 1 ms";
  if (ms < 1000) return `${Math.round(ms)} ms`;
  return `${(ms / 1000).toFixed(2)} s`;
}

export function formatInt(n: number): string {
  return n.toLocaleString();
}

export function formatCoord(deg: number, decimals = 5): string {
  return deg.toFixed(decimals);
}
