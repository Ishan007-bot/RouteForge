# RouteForge

A route planner and traffic simulator. Built in Java (engine + API) with a React + MapLibre frontend.
Engine is later ported to Rust behind the same API for a ~2-3x speedup.

## Status

Phase 4 — Web Frontend. React + TypeScript + Vite + MapLibre GL JS frontend
on top of the API. Click-to-place pins, profile/algo switchers, isochrone
overlay, side-by-side algorithm comparison.

Previous phases:
- Phase 0: Multi-module Maven scaffold + OSM PBF loader.
- Phase 1: CSR graph, car/bike/foot profiles, Dijkstra + A* + Bidirectional with indexed heap.
- Phase 2: Contraction Hierarchies (sub-millisecond queries on large graphs).
- Phase 3: Spring Boot REST API with Swagger, validation, in-memory cache, full test coverage.

## Repository layout

```
.
├── engine/        # Pure-Java routing engine: graph, profiles, A* / Dijkstra / CH
├── api/           # Spring Boot REST + WebSocket service (Phase 3+)
├── web/           # React + MapLibre frontend (Phase 4+)
├── data/          # OSM .pbf files, GTFS feeds (gitignored, you provide)
├── docker-compose.yml
├── .github/workflows/   # CI
└── pom.xml        # Parent Maven POM
```

## Prerequisites

- JDK 21 or newer (you have 25 — fine)
- Git
- (Later) Docker for Postgres + Redis
- (Later) Node 20+ for the frontend

You do **not** need Maven installed — the project uses the Maven Wrapper (`mvnw`).

## Quick start (Phase 0)

```powershell
# From the project root, on Windows:
.\mvnw.cmd verify
```

Or on macOS/Linux:

```bash
./mvnw verify
```

This compiles the engine, runs unit tests, and prints a summary.

## Try the OSM loader (Phase 0)

Download a small OSM extract (e.g. Liechtenstein, ~1.5 MB):

- https://download.geofabrik.de/europe/liechtenstein.html

Save it to `data/liechtenstein-latest.osm.pbf`, then:

```powershell
.\mvnw.cmd -pl engine compile exec:java `
  "-Dexec.mainClass=com.routeforge.engine.osm.OsmPbfLoader" `
  "-Dexec.args=data/liechtenstein-latest.osm.pbf"
```

Prints counts of nodes, ways, and relations.

## Try the router (Phase 1)

Same `.osm.pbf` file, then plan a route between two coordinates:

```powershell
.\mvnw.cmd -pl engine compile exec:java `
  "-Dexec.mainClass=com.routeforge.engine.cli.RouteCli" `
  "-Dexec.args=--pbf data/liechtenstein-latest.osm.pbf --from 47.142,9.524 --to 47.166,9.510 --profile car --algo astar"
```

Pass `--algo dijkstra | astar | bidirectional | ch` and
`--profile car | bike | foot` to compare. Output is JSON with route geometry,
distance, duration, and how many nodes the algorithm settled (useful for
comparing the algorithms head-to-head on the same query).

## Run the API (Phase 3)

```powershell
.\mvnw.cmd -pl api spring-boot:run `
  "-Dspring-boot.run.arguments=--routeforge.pbf-file=data/liechtenstein-latest.osm.pbf"
```

The service comes up on `http://localhost:8080`. Useful endpoints:

| Endpoint | What it does |
|---|---|
| `GET  /api/info`         | Engine and graph summary. |
| `POST /api/route`        | Plan a route between two coordinates. |
| `POST /api/isochrone`    | Area reachable from a point within a time budget. |
| `GET  /actuator/health`  | Health probe. |
| `GET  /swagger-ui.html`  | Interactive API docs (try-it-out). |

Quick `curl`:

```bash
curl -X POST http://localhost:8080/api/route \
  -H "Content-Type: application/json" \
  -d '{"fromLat":47.142,"fromLon":9.524,"toLat":47.166,"toLon":9.510,"profile":"car","algo":"astar"}'
```

## Run the web frontend (Phase 4)

First make sure the API is running (above), then in a second terminal:

```powershell
cd web
npm install      # one-time
npm run dev
```

Opens on `http://localhost:5173`. Vite proxies `/api/*` to the Spring Boot
backend so there are no CORS surprises in dev.

Features:
- Dark glassmorphic UI; vector tiles via OpenFreeMap.
- Click the map to place start/destination pins (drag to refine).
- Profile switcher (Car / Bike / Foot), algorithm switcher (Dijkstra / A★ / Bidirectional / CH).
- Routes auto-recompute on every change.
- **Compare all algorithms** button: runs every algorithm on the same query
  and shows distance, duration, search time, and nodes settled side-by-side.
- Isochrone mode: drag the budget slider to see the area reachable in N minutes
  as a heatmap overlay.
- Keyboard: `Esc` clears all pins.

## License

TBD.
