import { useEffect, useMemo, useRef, type MutableRefObject } from "react";
import maplibregl, { type Map as MlMap, type MapMouseEvent } from "maplibre-gl";
import { useAppStore } from "@/store/useAppStore";
import { profileColor, profileColorDeep } from "@/lib/theme";

const STYLE_URL = "https://tiles.openfreemap.org/styles/dark";

const SOURCE_ROUTE      = "route-src";
const LAYER_ROUTE_GLOW  = "route-glow";
const LAYER_ROUTE       = "route-line";
const SOURCE_ISO        = "iso-src";
const LAYER_ISO_HEAT    = "iso-heat";
const SOURCE_VEHICLES   = "sim-vehicles";
const LAYER_VEHICLES    = "sim-vehicles-pt";
const SOURCE_CLOSURES   = "sim-closures";
const LAYER_CLOSURES    = "sim-closures-line";

export function MapView() {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MlMap | null>(null);
  const mapReadyRef = useRef(false);

  const fromMarkerRef = useRef<maplibregl.Marker | null>(null);
  const toMarkerRef   = useRef<maplibregl.Marker | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;
    const map = new maplibregl.Map({
      container: containerRef.current,
      style: STYLE_URL,
      center: [9.5215, 47.154],
      zoom: 11,
      attributionControl: { compact: true },
      pitchWithRotate: false,
      dragRotate: false,
      touchZoomRotate: false,
    });
    mapRef.current = map;

    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), "bottom-right");
    map.addControl(new maplibregl.ScaleControl({ unit: "metric" }), "bottom-left");

    map.on("load", () => {
      mapReadyRef.current = true;
      setupSources(map);
      applyAll();
    });

    map.on("click", handleMapClick);
    map.on("moveend", () => {
      const c = map.getCenter();
      window.__mapCenter = { lat: c.lat, lon: c.lng };
    });

    return () => {
      map.off("click", handleMapClick);
      map.remove();
      mapRef.current = null;
      mapReadyRef.current = false;
      fromMarkerRef.current = null;
      toMarkerRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function handleMapClick(e: MapMouseEvent) {
    const state = useAppStore.getState();
    const pt = { lat: e.lngLat.lat, lon: e.lngLat.lng };

    if (state.mode === "simulator") {
      if (state.simClickAction === "close-edge") {
        void state.simCloseAt(pt.lat, pt.lon);
        return;
      }
      // Default click in simulator: also place A/B for the "from A→B" spawn helper.
      if (!state.from) state.setFrom(pt);
      else state.setTo(pt);
      return;
    }
    if (state.mode === "isochrone") { state.setFrom(pt); return; }

    // route mode
    if (!state.from) state.setFrom(pt);
    else if (!state.to) state.setTo(pt);
    else state.setTo(pt);
  }

  const from          = useAppStore((s) => s.from);
  const to            = useAppStore((s) => s.to);
  const route         = useAppStore((s) => s.route);
  const isochrone     = useAppStore((s) => s.isochrone);
  const profile       = useAppStore((s) => s.profile);
  const mode          = useAppStore((s) => s.mode);
  const simSnapshot   = useAppStore((s) => s.simSnapshot);
  const simClickAction = useAppStore((s) => s.simClickAction);

  // Markers
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    syncMarker(map, fromMarkerRef, from, "from", profile);
    syncMarker(map, toMarkerRef, mode === "route" || mode === "simulator" ? to : null, "to", profile);
  }, [from, to, profile, mode]);

  // Route layer (only meaningful in route mode)
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !mapReadyRef.current) return;
    const r = mode === "route" ? route : null;
    setRouteData(map, r, profile);
    if (r?.found && r.geometry.length > 1) fitToCoords(map, r.geometry, 80);
  }, [route, profile, mode]);

  // Isochrone layer
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !mapReadyRef.current) return;
    setIsoData(map, mode === "isochrone" ? isochrone : null);
  }, [isochrone, mode]);

  // Simulator: vehicles + closures
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !mapReadyRef.current) return;
    setSimData(map, mode === "simulator" ? simSnapshot : null);
  }, [simSnapshot, mode]);

  // Cursor reflects the click action.
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const canvas = map.getCanvas();
    canvas.style.cursor =
      mode === "simulator" && simClickAction === "close-edge" ? "crosshair" : "";
  }, [mode, simClickAction]);

  function applyAll() {
    const map = mapRef.current;
    if (!map) return;
    const s = useAppStore.getState();
    syncMarker(map, fromMarkerRef, s.from, "from", s.profile);
    syncMarker(map, toMarkerRef, s.mode === "route" || s.mode === "simulator" ? s.to : null, "to", s.profile);
    setRouteData(map, s.mode === "route" ? s.route : null, s.profile);
    setIsoData(map, s.mode === "isochrone" ? s.isochrone : null);
    setSimData(map, s.mode === "simulator" ? s.simSnapshot : null);
  }

  const hint = useMemo(() => {
    if (mode === "simulator") {
      if (simClickAction === "close-edge")
        return "Click any street to close it.";
      return "Click to drop A & B, then spawn from A → B in the side panel.";
    }
    if (mode === "isochrone")
      return "Click the map to place the origin.";
    if (!from) return "Click the map to set the start.";
    if (!to)   return "Click again to set the destination.";
    return null;
  }, [mode, from, to, simClickAction]);

  return (
    <div className="relative h-full w-full">
      <div ref={containerRef} className="absolute inset-0" />

      {hint && (
        <div className="pointer-events-none absolute bottom-5 left-1/2 z-10 -translate-x-1/2 animate-fade-up">
          <div className="rounded-sm border border-brass-700/50 bg-ink-900/95 px-3.5 py-1.5 font-mono text-[11px] text-paper-100 shadow-[0_10px_30px_-12px_rgba(0,0,0,0.7)]">
            <span className="mr-2 text-brass-400">·</span>
            {hint}
          </div>
        </div>
      )}
    </div>
  );
}

/* -------------------- sources & layers -------------------- */

function setupSources(map: MlMap) {
  map.addSource(SOURCE_ROUTE,    { type: "geojson", data: emptyFeatureCollection() });
  map.addSource(SOURCE_ISO,      { type: "geojson", data: emptyFeatureCollection() });
  map.addSource(SOURCE_VEHICLES, { type: "geojson", data: emptyFeatureCollection() });
  map.addSource(SOURCE_CLOSURES, { type: "geojson", data: emptyFeatureCollection() });

  // Route glow + line.
  map.addLayer({
    id: LAYER_ROUTE_GLOW, type: "line", source: SOURCE_ROUTE,
    layout: { "line-cap": "round", "line-join": "round" },
    paint: { "line-color": "#c9a35a", "line-width": 10, "line-opacity": 0.22, "line-blur": 6 },
  });
  map.addLayer({
    id: LAYER_ROUTE, type: "line", source: SOURCE_ROUTE,
    layout: { "line-cap": "round", "line-join": "round" },
    paint: { "line-color": "#c9a35a", "line-width": 3.5, "line-opacity": 0.98 },
  });

  // Isochrone heatmap.
  map.addLayer({
    id: LAYER_ISO_HEAT, type: "heatmap", source: SOURCE_ISO, maxzoom: 17,
    paint: {
      "heatmap-weight":    ["interpolate", ["linear"], ["get", "secs"], 0, 1.0, 3600, 0.1],
      "heatmap-intensity": ["interpolate", ["linear"], ["zoom"], 9, 0.6, 15, 2.2],
      "heatmap-color": [
        "interpolate", ["linear"], ["heatmap-density"],
        0,    "rgba(95, 168, 164, 0)",
        0.15, "rgba(95, 168, 164, 0.35)",
        0.45, "rgba(201, 163, 90, 0.55)",
        0.75, "rgba(200, 85, 61, 0.7)",
        1.0,  "rgba(200, 85, 61, 0.92)",
      ],
      "heatmap-radius": ["interpolate", ["linear"], ["zoom"], 9, 10, 13, 22, 16, 36],
      "heatmap-opacity": 0.85,
    },
  });

  // Sim closures (red dashed line over the closed edge).
  map.addLayer({
    id: LAYER_CLOSURES, type: "line", source: SOURCE_CLOSURES,
    layout: { "line-cap": "round", "line-join": "round" },
    paint: {
      "line-color": "#c8553d",
      "line-width": 5,
      "line-opacity": 0.85,
      "line-dasharray": [1.5, 1.5],
    },
  });

  // Sim vehicle dots, profile-tinted.
  map.addLayer({
    id: LAYER_VEHICLES, type: "circle", source: SOURCE_VEHICLES,
    paint: {
      "circle-radius": [
        "interpolate", ["linear"], ["zoom"],
        9, 2, 13, 4, 16, 6,
      ],
      "circle-color": [
        "match", ["get", "profile"],
        "car",  "#c9a35a",
        "bike", "#5fa8a4",
        "foot", "#c8553d",
        /* default */ "#c9a35a",
      ],
      "circle-stroke-color": "#0a0c0f",
      "circle-stroke-width": 1,
      "circle-opacity": [
        "match", ["get", "status"],
        "active",  0.95,
        "stuck",   0.45,
        /* default */ 0.7,
      ],
    },
  });
}

function setRouteData(map: MlMap, route: import("@/lib/types").RouteResponse | null, profile: string) {
  const src = map.getSource(SOURCE_ROUTE) as maplibregl.GeoJSONSource | undefined;
  if (!src) return;
  if (!route || !route.found || route.geometry.length < 2) { src.setData(emptyFeatureCollection()); return; }
  const coords = route.geometry.map(([lat, lon]) => [lon, lat]);
  src.setData({
    type: "FeatureCollection",
    features: [{ type: "Feature", properties: {}, geometry: { type: "LineString", coordinates: coords } }],
  });
  const color = profileColor(profile);
  map.setPaintProperty(LAYER_ROUTE,      "line-color", color);
  map.setPaintProperty(LAYER_ROUTE_GLOW, "line-color", color);
}

function setIsoData(map: MlMap, iso: import("@/lib/types").IsochroneResponse | null) {
  const src = map.getSource(SOURCE_ISO) as maplibregl.GeoJSONSource | undefined;
  if (!src) return;
  if (!iso || iso.points.length === 0) { src.setData(emptyFeatureCollection()); return; }
  src.setData({
    type: "FeatureCollection",
    features: iso.points.map(([lat, lon, secs]) => ({
      type: "Feature",
      properties: { secs },
      geometry: { type: "Point", coordinates: [lon, lat] },
    })),
  });
}

function setSimData(map: MlMap, snap: import("@/lib/types").SimSnapshot | null) {
  const vSrc = map.getSource(SOURCE_VEHICLES) as maplibregl.GeoJSONSource | undefined;
  const cSrc = map.getSource(SOURCE_CLOSURES) as maplibregl.GeoJSONSource | undefined;
  if (!vSrc || !cSrc) return;
  if (!snap) {
    vSrc.setData(emptyFeatureCollection());
    cSrc.setData(emptyFeatureCollection());
    return;
  }
  vSrc.setData({
    type: "FeatureCollection",
    features: snap.vehicles.map((v) => ({
      type: "Feature",
      properties: { id: v.id, profile: v.profile, status: v.status, heading: v.heading },
      geometry: { type: "Point", coordinates: [v.lon, v.lat] },
    })),
  });
  cSrc.setData({
    type: "FeatureCollection",
    features: snap.closedEdgeGeoms.map((e) => ({
      type: "Feature",
      properties: { edgeId: e.edgeId },
      geometry: { type: "LineString", coordinates: [[e.fromLon, e.fromLat], [e.toLon, e.toLat]] },
    })),
  });
}

function syncMarker(
  map: MlMap,
  ref: MutableRefObject<maplibregl.Marker | null>,
  pos: { lat: number; lon: number } | null,
  kind: "from" | "to",
  profile: string,
) {
  if (!pos) { ref.current?.remove(); ref.current = null; return; }
  ref.current?.remove();
  const el = makePinElement(kind, profile);
  const m = new maplibregl.Marker({ element: el, anchor: "bottom", draggable: true })
    .setLngLat([pos.lon, pos.lat])
    .addTo(map);
  m.on("dragend", () => {
    const ll = m.getLngLat();
    const state = useAppStore.getState();
    if (kind === "from") state.setFrom({ lat: ll.lat, lon: ll.lng });
    else                 state.setTo({   lat: ll.lat, lon: ll.lng });
  });
  ref.current = m;
}

function makePinElement(kind: "from" | "to", profile: string): HTMLDivElement {
  const wrap = document.createElement("div");
  wrap.style.position = "relative";
  wrap.style.transform = "translate(-50%, -100%)";
  wrap.style.pointerEvents = "auto";
  wrap.style.cursor = "grab";
  wrap.style.willChange = "transform";

  const accent = kind === "from" ? profileColor(profile) : "#c8553d";
  const deep   = kind === "from" ? profileColorDeep(profile) : "#7d3024";
  const label  = kind === "from" ? "A" : "B";

  wrap.innerHTML = `
    <div style="position:absolute; left:50%; top:14px; transform:translate(-50%,-50%);
                width:30px; height:30px; border-radius:9999px;
                background:${accent}33;
                animation: pulsePin 1.8s ease-out infinite;"></div>
    <div style="position:relative; display:flex; align-items:center; justify-content:center;
                width:28px; height:28px; border-radius:9999px;
                background:radial-gradient(circle at 30% 30%, ${accent} 0%, ${deep} 75%);
                color:#0a0c0f;
                font-family:'Manrope', system-ui, sans-serif;
                font-weight:700; font-size:13px; line-height:1; letter-spacing:0.02em;
                box-shadow:
                  inset 0 0 0 1px rgba(0,0,0,0.25),
                  inset 0 0 0 2px rgba(255,255,255,0.20),
                  0 6px 16px rgba(0,0,0,0.55);">
      ${label}
    </div>
    <div style="margin:0 auto; width:1.5px; height:10px; background:linear-gradient(to bottom, ${deep}, ${deep}99 60%, transparent);"></div>
    <div style="margin:-3px auto 0; width:5px; height:5px; transform:rotate(45deg);
                background:${deep}; box-shadow:0 1px 2px rgba(0,0,0,0.6);"></div>
  `;

  if (!document.getElementById("pin-keyframes")) {
    const style = document.createElement("style");
    style.id = "pin-keyframes";
    style.textContent = `
      @keyframes pulsePin {
        0%   { transform: translate(-50%,-50%) scale(1);   opacity: 0.6; }
        100% { transform: translate(-50%,-50%) scale(2.4); opacity: 0;   }
      }
    `;
    document.head.appendChild(style);
  }
  return wrap;
}

function fitToCoords(map: MlMap, latlon: [number, number][], padding: number) {
  const bounds = new maplibregl.LngLatBounds();
  for (const [lat, lon] of latlon) bounds.extend([lon, lat]);
  if (bounds.isEmpty()) return;
  map.fitBounds(bounds, { padding, duration: 800, maxZoom: 16 });
}

function emptyFeatureCollection(): GeoJSON.FeatureCollection {
  return { type: "FeatureCollection", features: [] };
}
