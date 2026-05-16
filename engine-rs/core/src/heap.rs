//! Indexed binary min-heap with O(log n) decrease-key.
//!
//! Rust's `std::collections::BinaryHeap` is a max-heap with no
//! decrease-key — fine for "lazy" Dijkstra (push duplicates, skip
//! stale entries) but worse cache behaviour and worse asymptotics on
//! high-degree graphs. We use the indexed variant, same as the Java
//! engine, so each node sits in the heap at most once.
//!
//! The heap stores node ids as `u32`. Keys live in a parallel `f64`
//! array sized to the node count; `position[node]` tracks where each
//! node currently sits in the heap (or `-1` if absent).

pub struct IndexedBinaryHeap {
    /// `heap[i]` is the node id at heap position `i`.
    heap: Vec<u32>,
    /// `keys[node]` is the current best cost for `node`.
    keys: Vec<f64>,
    /// `position[node]` is the heap index of `node`, or `-1` if not present.
    position: Vec<i32>,
}

impl IndexedBinaryHeap {
    pub fn with_capacity(node_count: usize) -> Self {
        Self {
            heap:     Vec::with_capacity(node_count),
            keys:     vec![f64::INFINITY; node_count],
            position: vec![-1i32; node_count],
        }
    }

    #[inline] pub fn len(&self) -> usize { self.heap.len() }
    #[inline] pub fn is_empty(&self) -> bool { self.heap.is_empty() }

    #[inline] pub fn key_of(&self, node: u32) -> f64 { self.keys[node as usize] }
    #[inline] pub fn contains(&self, node: u32) -> bool { self.position[node as usize] >= 0 }

    /// Either insert `node` with `key`, or decrease its key if the new
    /// one is smaller. Returns `true` if the heap state changed.
    pub fn push_or_decrease(&mut self, node: u32, key: f64) -> bool {
        let pos = self.position[node as usize];
        if pos < 0 {
            self.keys[node as usize] = key;
            self.heap.push(node);
            let new_pos = (self.heap.len() - 1) as i32;
            self.position[node as usize] = new_pos;
            self.sift_up(new_pos as usize);
            true
        } else if key < self.keys[node as usize] {
            self.keys[node as usize] = key;
            self.sift_up(pos as usize);
            true
        } else {
            false
        }
    }

    /// Inspect the smallest-keyed node without removing it.
    #[inline]
    pub fn peek_min(&self) -> Option<u32> { self.heap.first().copied() }

    /// Remove and return the node with the smallest key.
    pub fn pop_min(&mut self) -> Option<u32> {
        if self.heap.is_empty() { return None; }
        let top = self.heap[0];
        let last = self.heap.pop().unwrap();
        self.position[top as usize] = -1;
        if !self.heap.is_empty() {
            self.heap[0] = last;
            self.position[last as usize] = 0;
            self.sift_down(0);
        }
        Some(top)
    }

    /* -------- internals -------- */

    fn sift_up(&mut self, mut i: usize) {
        while i > 0 {
            let parent = (i - 1) / 2;
            if self.keys[self.heap[i] as usize] < self.keys[self.heap[parent] as usize] {
                self.swap(i, parent);
                i = parent;
            } else {
                break;
            }
        }
    }

    fn sift_down(&mut self, mut i: usize) {
        let n = self.heap.len();
        loop {
            let l = 2 * i + 1;
            let r = 2 * i + 2;
            let mut smallest = i;
            if l < n && self.keys[self.heap[l] as usize] < self.keys[self.heap[smallest] as usize] {
                smallest = l;
            }
            if r < n && self.keys[self.heap[r] as usize] < self.keys[self.heap[smallest] as usize] {
                smallest = r;
            }
            if smallest == i { break; }
            self.swap(i, smallest);
            i = smallest;
        }
    }

    #[inline]
    fn swap(&mut self, a: usize, b: usize) {
        self.heap.swap(a, b);
        self.position[self.heap[a] as usize] = a as i32;
        self.position[self.heap[b] as usize] = b as i32;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pops_in_ascending_order() {
        let mut h = IndexedBinaryHeap::with_capacity(8);
        h.push_or_decrease(3, 5.0);
        h.push_or_decrease(1, 1.0);
        h.push_or_decrease(2, 9.0);
        h.push_or_decrease(0, 4.0);
        assert_eq!(h.pop_min(), Some(1));
        assert_eq!(h.pop_min(), Some(0));
        assert_eq!(h.pop_min(), Some(3));
        assert_eq!(h.pop_min(), Some(2));
        assert!(h.pop_min().is_none());
    }

    #[test]
    fn decrease_key_re_sifts_into_position() {
        let mut h = IndexedBinaryHeap::with_capacity(4);
        h.push_or_decrease(0, 10.0);
        h.push_or_decrease(1, 5.0);
        h.push_or_decrease(2, 7.0);
        // Now decrease 0 below the current minimum.
        assert!(h.push_or_decrease(0, 1.0));
        assert_eq!(h.pop_min(), Some(0));
    }

    #[test]
    fn decrease_with_larger_key_is_ignored() {
        let mut h = IndexedBinaryHeap::with_capacity(2);
        h.push_or_decrease(0, 5.0);
        assert!(!h.push_or_decrease(0, 9.0));
        assert_eq!(h.key_of(0), 5.0);
    }
}
