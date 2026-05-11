package com.routeforge.engine.algo;

import java.util.Arrays;

/**
 * Min-heap of integer keys (graph node indices) with associated double priorities,
 * supporting the {@code decreaseKey} operation in O(log n).
 *
 * <h2>Why we need this</h2>
 * Plain {@link java.util.PriorityQueue} doesn't expose decrease-key. When
 * Dijkstra finds a shorter path to a node already in the queue, the textbook
 * algorithm "decreases" its priority. With {@code PriorityQueue} you can't
 * find the existing entry to update — so people work around it by inserting
 * a duplicate and ignoring stale pops. That works but wastes memory and
 * makes the queue larger than n, hurting cache behavior.
 * <p>
 * An <i>indexed</i> heap keeps a {@code position[]} array: {@code position[node]}
 * is where that node currently sits in the heap. With that index we can find
 * the slot in O(1) and sift up/down from there.
 *
 * <h2>Layout</h2>
 * Standard binary heap with array indexing:
 * <pre>
 *   parent(i) = (i - 1) / 2
 *   left(i)   = 2*i + 1
 *   right(i)  = 2*i + 2
 * </pre>
 * Smaller priority = closer to the root.
 */
public final class IndexedBinaryHeap {

    /** Marker meaning "this node is not currently in the heap". */
    public static final int NOT_IN_HEAP = -1;

    private final int capacity;     // total number of distinct keys (= node count)
    private final int[]    heap;    // heap[i] = node key at heap position i
    private final int[]    position;// position[node] = current heap position, or NOT_IN_HEAP
    private final double[] priority;// priority[node] = current priority (only valid if in heap)
    private int size;

    public IndexedBinaryHeap(int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("capacity < 0");
        this.capacity = capacity;
        this.heap = new int[capacity];
        this.position = new int[capacity];
        this.priority = new double[capacity];
        Arrays.fill(position, NOT_IN_HEAP);
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
    public boolean contains(int node) { return position[node] != NOT_IN_HEAP; }
    public double priorityOf(int node) { return priority[node]; }

    /**
     * Insert {@code node} with the given priority, or decrease its existing
     * priority if it's already in the heap and {@code newPriority} is smaller.
     * <p>
     * Returns {@code true} if the heap was changed (insert or decrease),
     * {@code false} if the node was already present with a priority &lt;= newPriority.
     */
    public boolean insertOrDecrease(int node, double newPriority) {
        int pos = position[node];
        if (pos == NOT_IN_HEAP) {
            // Fresh insert: place at end, sift up.
            heap[size] = node;
            position[node] = size;
            priority[node] = newPriority;
            siftUp(size);
            size++;
            return true;
        }
        if (newPriority < priority[node]) {
            priority[node] = newPriority;
            siftUp(pos);
            return true;
        }
        return false;
    }

    /**
     * Remove and return the node with the smallest priority.
     * @throws IllegalStateException if empty
     */
    public int pollMin() {
        if (size == 0) throw new IllegalStateException("heap is empty");
        int min = heap[0];
        size--;
        if (size > 0) {
            // Move last element to root, then sift down.
            int last = heap[size];
            heap[0] = last;
            position[last] = 0;
            siftDown(0);
        }
        position[min] = NOT_IN_HEAP;
        return min;
    }

    /** Peek at the minimum without removing it. */
    public int peekMin() {
        if (size == 0) throw new IllegalStateException("heap is empty");
        return heap[0];
    }

    /** Reset to empty, reusable for the next search. O(n) only on used slots. */
    public void clear() {
        for (int i = 0; i < size; i++) {
            position[heap[i]] = NOT_IN_HEAP;
        }
        size = 0;
    }

    // ---------- Internal sift operations ----------

    private void siftUp(int i) {
        // Cache the node being moved to avoid repeated array writes during the walk.
        int node = heap[i];
        double p = priority[node];
        while (i > 0) {
            int parent = (i - 1) >>> 1;
            int parentNode = heap[parent];
            if (priority[parentNode] <= p) break;
            // Pull parent down.
            heap[i] = parentNode;
            position[parentNode] = i;
            i = parent;
        }
        heap[i] = node;
        position[node] = i;
    }

    private void siftDown(int i) {
        int node = heap[i];
        double p = priority[node];
        int half = size >>> 1;            // first leaf index
        while (i < half) {
            int left  = 2 * i + 1;
            int right = left + 1;
            int smaller = left;
            if (right < size && priority[heap[right]] < priority[heap[left]]) {
                smaller = right;
            }
            int smallerNode = heap[smaller];
            if (p <= priority[smallerNode]) break;
            heap[i] = smallerNode;
            position[smallerNode] = i;
            i = smaller;
        }
        heap[i] = node;
        position[node] = i;
    }

    public int capacity() { return capacity; }
}
