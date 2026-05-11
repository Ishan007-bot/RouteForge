package com.routeforge.engine.algo;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexedBinaryHeapTest {

    @Test
    void emptyHeap() {
        var h = new IndexedBinaryHeap(4);
        assertThat(h.isEmpty()).isTrue();
        assertThat(h.size()).isZero();
        assertThatThrownBy(h::pollMin).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void insertAndPollInPriorityOrder() {
        var h = new IndexedBinaryHeap(5);
        h.insertOrDecrease(0, 5.0);
        h.insertOrDecrease(1, 2.0);
        h.insertOrDecrease(2, 9.0);
        h.insertOrDecrease(3, 1.0);
        h.insertOrDecrease(4, 7.0);
        assertThat(h.size()).isEqualTo(5);

        // Polled order = sorted by priority ascending.
        assertThat(h.pollMin()).isEqualTo(3); // priority 1.0
        assertThat(h.pollMin()).isEqualTo(1); // priority 2.0
        assertThat(h.pollMin()).isEqualTo(0); // priority 5.0
        assertThat(h.pollMin()).isEqualTo(4); // priority 7.0
        assertThat(h.pollMin()).isEqualTo(2); // priority 9.0
        assertThat(h.isEmpty()).isTrue();
    }

    @Test
    void decreaseKey_movesUp() {
        var h = new IndexedBinaryHeap(3);
        h.insertOrDecrease(0, 10.0);
        h.insertOrDecrease(1, 20.0);
        h.insertOrDecrease(2, 30.0);
        // Decrease node 2 from 30 to 1.
        assertThat(h.insertOrDecrease(2, 1.0)).isTrue();
        assertThat(h.pollMin()).isEqualTo(2);
    }

    @Test
    void increasing_priority_is_ignored() {
        var h = new IndexedBinaryHeap(2);
        h.insertOrDecrease(0, 5.0);
        // 10 > 5, so heap shouldn't change.
        assertThat(h.insertOrDecrease(0, 10.0)).isFalse();
        assertThat(h.priorityOf(0)).isEqualTo(5.0);
    }

    @Test
    void containsAndPriorityLifecycle() {
        var h = new IndexedBinaryHeap(2);
        assertThat(h.contains(0)).isFalse();
        h.insertOrDecrease(0, 4.2);
        assertThat(h.contains(0)).isTrue();
        assertThat(h.priorityOf(0)).isEqualTo(4.2);
        h.pollMin();
        assertThat(h.contains(0)).isFalse();
    }

    /** Stress test: shove random pushes and decreases at the heap and check
     *  the output is sorted. */
    @Test
    void randomStress() {
        Random rng = new Random(42);
        int n = 5000;
        var h = new IndexedBinaryHeap(n);
        double[] best = new double[n];
        java.util.Arrays.fill(best, Double.POSITIVE_INFINITY);

        for (int op = 0; op < n * 4; op++) {
            int node = rng.nextInt(n);
            double prio = rng.nextDouble() * 1_000_000;
            h.insertOrDecrease(node, prio);
            if (prio < best[node]) best[node] = prio;
        }

        List<Double> popped = new ArrayList<>();
        while (!h.isEmpty()) popped.add(best[h.pollMin()]);

        // popped should be non-decreasing.
        for (int i = 1; i < popped.size(); i++) {
            assertThat(popped.get(i)).isGreaterThanOrEqualTo(popped.get(i - 1));
        }

        List<Double> sorted = new ArrayList<>(popped);
        Collections.sort(sorted);
        assertThat(popped).isEqualTo(sorted);
    }
}
