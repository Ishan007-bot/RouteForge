package com.routeforge.engine.sim;

import com.routeforge.engine.algo.AStar;
import com.routeforge.engine.algo.ShortestPath;
import com.routeforge.engine.graph.RoadGraph;
import com.routeforge.engine.profile.Profile;
import com.routeforge.engine.routing.RouteResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Discrete-time traffic simulation on a {@link RoadGraph}.
 *
 * <h2>Model</h2>
 * <ul>
 *   <li>Time advances in fixed {@value DEFAULT_TICK_MS} ms ticks, scaled by
 *       a runtime speed multiplier.</li>
 *   <li>A vehicle has a planned path expressed as an edge sequence, plus a
 *       position along its current edge in meters.</li>
 *   <li>Edge loads are tracked in a {@link TrafficModel}. Routing uses a
 *       {@link TrafficAwareProfile} so vehicles avoid congested or closed
 *       roads.</li>
 *   <li>When an edge a vehicle is about to use becomes invalid (closed), the
 *       vehicle reroutes from its current node to its destination.</li>
 * </ul>
 *
 * <h2>Threading</h2>
 * All state mutation runs on a single scheduled "sim-tick" thread. External
 * callers submit work via {@link #post(Runnable)}; commands run at the top
 * of the next tick. Snapshots are pushed to {@link Listener listeners} at
 * the end of every tick.
 */
public final class SimulationEngine {

    /** Notified at the end of every tick with the new snapshot. */
    public interface Listener { void onTick(Snapshot snapshot); }

    /** Immutable per-vehicle row in a snapshot. */
    public record VehicleSnap(
            int id, double lat, double lon, double heading,
            String status, String profile,
            double progressFraction
    ) { }

    /** Immutable simulation snapshot, broadcast every tick. */
    public record Snapshot(
            long tick,
            double simSeconds,
            int activeVehicles,
            int arrivedVehicles,
            int stuckVehicles,
            int congestedEdges,
            int closedEdges,
            double speedMultiplier,
            boolean running,
            List<VehicleSnap> vehicles,
            List<EdgeSnap> closedEdgeGeoms
    ) { }

    /** Geometry for a flagged edge (closure) so the frontend can draw it. */
    public record EdgeSnap(int edgeId, double fromLat, double fromLon, double toLat, double toLon) { }

    private static final long DEFAULT_TICK_MS = 100;
    private static final double TICK_SECONDS = DEFAULT_TICK_MS / 1000.0;

    private final RoadGraph graph;
    private final TrafficModel traffic;
    private final Map<String, Profile> profiles;
    /** Reroutes use A* with a TrafficAwareProfile (CH can't see dynamic weights). */
    private final ShortestPath router = new AStar();

    private final List<Vehicle> vehicles = new ArrayList<>();
    private final ConcurrentLinkedQueue<Runnable> commandQueue = new ConcurrentLinkedQueue<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private final AtomicInteger nextVehicleId = new AtomicInteger(0);
    private final AtomicLong tickCounter = new AtomicLong(0);

    private ScheduledExecutorService exec;
    private ScheduledFuture<?> handle;
    private volatile boolean running = false;
    private volatile double speedMultiplier = 1.0;
    private int arrivedCount = 0;
    private int stuckCount = 0;

    public SimulationEngine(RoadGraph graph, Map<String, Profile> profiles) {
        this.graph    = graph;
        this.traffic  = new TrafficModel(graph.edgeCount());
        this.profiles = new HashMap<>(profiles);
    }

    /* -------------------- lifecycle -------------------- */

    public synchronized void start() {
        if (running) return;
        running = true;
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sim-tick");
            t.setDaemon(true);
            return t;
        });
        handle = exec.scheduleAtFixedRate(this::tickSafe, 0, DEFAULT_TICK_MS, TimeUnit.MILLISECONDS);
    }

    public synchronized void pause() {
        if (!running) return;
        running = false;
        if (handle != null) handle.cancel(false);
        if (exec != null) exec.shutdownNow();
        handle = null;
        exec = null;
    }

    public synchronized void reset() {
        pause();
        commandQueue.clear();
        vehicles.clear();
        arrivedCount = 0;
        stuckCount = 0;
        for (int e = 0; e < traffic.edgeCount(); e++) {
            traffic.open(e);
            // load is naturally drained by vehicles.clear(); but reset to be safe.
            while (traffic.load(e) > 0) traffic.leave(e);
        }
        tickCounter.set(0);
        nextVehicleId.set(0);
    }

    public boolean isRunning() { return running; }
    public double speedMultiplier() { return speedMultiplier; }
    public void setSpeedMultiplier(double x) {
        speedMultiplier = Math.max(0.1, Math.min(8.0, x));
    }

    public void addListener(Listener l)    { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }
    public void post(Runnable cmd)         { commandQueue.offer(cmd); }

    public TrafficModel traffic() { return traffic; }
    public RoadGraph graph() { return graph; }

    /* -------------------- commands (run on tick thread) -------------------- */

    /**
     * Schedule a vehicle to spawn at the next tick.
     * Returns the id the vehicle will have. The spawn may still fail (e.g.
     * no route possible); the resulting vehicle will then be reported as
     * STUCK in the snapshot.
     */
    public int spawn(double fromLat, double fromLon,
                     double toLat,   double toLon,
                     String profileName) {
        int id = nextVehicleId.getAndIncrement();
        post(() -> {
            Profile base = profiles.get(profileName);
            if (base == null) return;
            Vehicle v = new Vehicle(id, profileName, toLat, toLon);
            int srcNode = graph.nearestNode(fromLat, fromLon);
            if (srcNode == -1) {
                v.status = Vehicle.Status.STUCK;
                stuckCount++;
                vehicles.add(v);
                return;
            }
            v.lat = graph.lat(srcNode);
            v.lon = graph.lon(srcNode);
            if (!planRoute(v, srcNode, base)) {
                v.status = Vehicle.Status.STUCK;
                stuckCount++;
            }
            vehicles.add(v);
        });
        return id;
    }

    public void closeEdge(int edge) {
        post(() -> {
            if (edge < 0 || edge >= graph.edgeCount()) return;
            if (traffic.isClosed(edge)) return;
            traffic.close(edge);
            for (Vehicle v : vehicles) {
                if (v.status != Vehicle.Status.ACTIVE || v.edges == null) continue;
                for (int i = v.edgeCursor; i < v.edges.length; i++) {
                    if (v.edges[i] == edge) { reroute(v); break; }
                }
            }
        });
    }

    public void openEdge(int edge) {
        post(() -> {
            if (edge < 0 || edge >= graph.edgeCount()) return;
            traffic.open(edge);
        });
    }

    /**
     * Find the edge whose midpoint is nearest to (lat, lon) among edges
     * incident to the nearest graph node, then close it. Returns -1 if no
     * candidate exists (e.g. empty graph). Runs on the tick thread.
     */
    public int closeNearestEdge(double lat, double lon) {
        int edge = pickNearestEdge(lat, lon);
        if (edge >= 0) closeEdge(edge);
        return edge;
    }

    public int pickNearestEdge(double lat, double lon) {
        int n = graph.nearestNode(lat, lon);
        if (n < 0) return -1;
        int bestEdge = -1;
        double bestDist = Double.POSITIVE_INFINITY;
        // Outgoing edges from the snapped node.
        for (int e = graph.firstEdge(n); e < graph.endEdge(n); e++) {
            int t = graph.target(e);
            double mlat = (graph.lat(n) + graph.lat(t)) * 0.5;
            double mlon = (graph.lon(n) + graph.lon(t)) * 0.5;
            double d = (mlat - lat) * (mlat - lat) + (mlon - lon) * (mlon - lon);
            if (d < bestDist) { bestDist = d; bestEdge = e; }
        }
        return bestEdge;
    }

    public void despawnAll() {
        post(() -> {
            for (Vehicle v : vehicles) {
                if (v.status == Vehicle.Status.ACTIVE) leaveAllEdges(v);
            }
            vehicles.clear();
            arrivedCount = 0;
            stuckCount = 0;
        });
    }

    /* -------------------- tick loop -------------------- */

    private void tickSafe() {
        try { tick(); }
        catch (Throwable t) { t.printStackTrace(); }
    }

    private void tick() {
        Runnable cmd;
        while ((cmd = commandQueue.poll()) != null) {
            try { cmd.run(); } catch (Throwable t) { t.printStackTrace(); }
        }

        double dt = TICK_SECONDS * speedMultiplier;
        for (Vehicle v : vehicles) {
            if (v.status != Vehicle.Status.ACTIVE) continue;
            advanceVehicle(v, dt);
        }

        tickCounter.incrementAndGet();
        Snapshot snap = buildSnapshot();
        for (Listener l : listeners) {
            try { l.onTick(snap); } catch (Throwable t) { /* keep loop alive */ }
        }
    }

    private void advanceVehicle(Vehicle v, double dt) {
        if (v.edges == null || v.edgeCursor >= v.edges.length) {
            v.status = Vehicle.Status.ARRIVED;
            return;
        }
        Profile base = profiles.get(v.profileName);
        if (base == null) { v.status = Vehicle.Status.STUCK; stuckCount++; return; }

        int curEdge = v.edges[v.edgeCursor];
        double freeFlow = freeFlowSpeed(base, curEdge);
        double effSpeed = freeFlow / traffic.congestionFactor(curEdge);
        double stepMeters = effSpeed * dt;
        v.totalDistance += stepMeters;
        v.progressMeters += stepMeters;

        // Cross edge boundaries.
        while (v.progressMeters >= graph.lengthMeters(curEdge)) {
            v.progressMeters -= graph.lengthMeters(curEdge);
            traffic.leave(curEdge);
            v.edgeCursor++;
            if (v.edgeCursor >= v.edges.length) {
                v.status = Vehicle.Status.ARRIVED;
                v.lat = graph.lat(graph.target(curEdge));
                v.lon = graph.lon(graph.target(curEdge));
                v.progressMeters = 0;
                arrivedCount++;
                return;
            }
            curEdge = v.edges[v.edgeCursor];
            if (traffic.isClosed(curEdge)) {
                if (!reroute(v)) { v.status = Vehicle.Status.STUCK; stuckCount++; return; }
                if (v.edges == null) { v.status = Vehicle.Status.STUCK; stuckCount++; return; }
                curEdge = v.edges[v.edgeCursor];
            }
            traffic.enter(curEdge);
        }

        // Interpolate position along the current edge.
        int src = graph.source(curEdge);
        int tgt = graph.target(curEdge);
        double len = Math.max(graph.lengthMeters(curEdge), 1e-9);
        double frac = Math.min(1.0, v.progressMeters / len);
        v.lat = lerp(graph.lat(src), graph.lat(tgt), frac);
        v.lon = lerp(graph.lon(src), graph.lon(tgt), frac);
        v.heading = bearing(graph.lat(src), graph.lon(src), graph.lat(tgt), graph.lon(tgt));
    }

    /* -------------------- routing -------------------- */

    private boolean planRoute(Vehicle v, int srcNode, Profile base) {
        int tgtNode = graph.nearestNode(v.destLat, v.destLon);
        if (tgtNode == -1) return false;
        if (srcNode == tgtNode) return false;
        Profile tp = new TrafficAwareProfile(base, traffic);
        RouteResult r = router.shortestPath(graph, srcNode, tgtNode, tp);
        if (!r.found() || r.nodePath().size() < 2) return false;
        int[] edges = nodesToEdges(r.nodePath());
        if (edges == null) return false;
        v.edges          = edges;
        v.edgeCursor     = 0;
        v.progressMeters = 0.0;
        traffic.enter(edges[0]);
        return true;
    }

    private boolean reroute(Vehicle v) {
        v.reroutes++;
        leaveAllEdges(v);
        Profile base = profiles.get(v.profileName);
        if (base == null) return false;
        int currentNode = graph.nearestNode(v.lat, v.lon);
        if (currentNode == -1) return false;
        return planRoute(v, currentNode, base);
    }

    private void leaveAllEdges(Vehicle v) {
        if (v.edges == null) return;
        for (int i = v.edgeCursor; i < v.edges.length; i++) {
            traffic.leave(v.edges[i]);
        }
        v.edges = null;
        v.edgeCursor = 0;
        v.progressMeters = 0;
    }

    private int[] nodesToEdges(List<Integer> nodePath) {
        if (nodePath.size() < 2) return null;
        int[] edges = new int[nodePath.size() - 1];
        for (int i = 0; i < edges.length; i++) {
            int from = nodePath.get(i);
            int to   = nodePath.get(i + 1);
            int found = -1;
            for (int e = graph.firstEdge(from); e < graph.endEdge(from); e++) {
                if (graph.target(e) == to) { found = e; break; }
            }
            if (found < 0) return null;
            edges[i] = found;
        }
        return edges;
    }

    private double freeFlowSpeed(Profile base, int edge) {
        double dur = base.cost(graph, edge);
        if (!Double.isFinite(dur) || dur <= 0) return base.maxSpeedMetersPerSecond();
        return graph.lengthMeters(edge) / dur;
    }

    /* -------------------- snapshot -------------------- */

    private Snapshot buildSnapshot() {
        int activeN = 0, congested = 0, closedN = 0;
        List<EdgeSnap> closedGeoms = new ArrayList<>();
        int capacityThreshold = Math.max(1, traffic.capacity() / 2);
        for (int e = 0; e < traffic.edgeCount(); e++) {
            int l = traffic.load(e);
            if (l >= capacityThreshold) congested++;
            if (traffic.isClosed(e)) {
                closedN++;
                int s = graph.source(e);
                int t = graph.target(e);
                closedGeoms.add(new EdgeSnap(e, graph.lat(s), graph.lon(s), graph.lat(t), graph.lon(t)));
            }
        }

        List<VehicleSnap> vs = new ArrayList<>(vehicles.size());
        for (Vehicle v : vehicles) {
            if (v.status == Vehicle.Status.ARRIVED) continue;
            if (v.status == Vehicle.Status.ACTIVE) activeN++;
            double frac = 0;
            if (v.edges != null && v.edges.length > 0) {
                double curEdgeLen = Math.max(graph.lengthMeters(v.edges[Math.min(v.edgeCursor, v.edges.length - 1)]), 1e-9);
                double done = v.edgeCursor + Math.min(1.0, v.progressMeters / curEdgeLen);
                frac = Math.min(1.0, done / v.edges.length);
            }
            vs.add(new VehicleSnap(
                    v.id, v.lat, v.lon, v.heading,
                    v.status.name().toLowerCase(java.util.Locale.ROOT),
                    v.profileName, frac
            ));
        }

        return new Snapshot(
                tickCounter.get(),
                tickCounter.get() * TICK_SECONDS,
                activeN,
                arrivedCount,
                stuckCount,
                congested,
                closedN,
                speedMultiplier,
                running,
                vs,
                closedGeoms
        );
    }

    /* -------------------- math -------------------- */

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2)
                 - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLon);
        double brng = Math.toDegrees(Math.atan2(y, x));
        return (brng + 360.0) % 360.0;
    }
}
