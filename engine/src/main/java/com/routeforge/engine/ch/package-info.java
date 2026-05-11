/**
 * Contraction Hierarchies.
 * <p>
 * A preprocessing technique that adds <i>shortcut edges</i> to a road graph
 * so that the bidirectional Dijkstra-style query only needs to follow
 * "upward" edges in a per-node level ordering. Queries become orders of
 * magnitude faster than plain Dijkstra/A* on continental graphs at the cost
 * of a one-time preprocessing pass.
 *
 * <h2>Pieces</h2>
 * <ul>
 *   <li>{@link com.routeforge.engine.ch.CHPreprocessor} — runs once per (graph, profile)
 *       pair; produces a {@link com.routeforge.engine.ch.CHGraph}.</li>
 *   <li>{@link com.routeforge.engine.ch.CHGraph} — immutable artifact: original
 *       graph + node levels + shortcut edges + upward adjacencies.</li>
 *   <li>{@link com.routeforge.engine.ch.CHQuery} — bidirectional upward search.</li>
 * </ul>
 */
package com.routeforge.engine.ch;
