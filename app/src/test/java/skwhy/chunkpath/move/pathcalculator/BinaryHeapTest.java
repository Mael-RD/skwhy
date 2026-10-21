package skwhy.chunkpath.move.pathcalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class BinaryHeapTest {

    private static Node node(int i, float f) {
        Node n = new Node(i, 0, 0);
        n.f = f;
        return n;
    }

    @Test
    void popsInIncreasingCostOrder() {
        BinaryHeap heap = new BinaryHeap();
        float[] costs = {5, 1, 4, 2, 3, 9, 0, 7};
        for (int i = 0; i < costs.length; i++) heap.insert(node(i, costs[i]));
        assertEquals(costs.length, heap.size());
        float previous = -1;
        while (!heap.isEmpty()) {
            float f = heap.pop().f;
            assertTrue(f >= previous);
            previous = f;
        }
    }

    @Test
    void peekDoesNotRemove() {
        BinaryHeap heap = new BinaryHeap();
        heap.insert(node(0, 2));
        Node low = heap.insert(node(1, 1));
        assertSame(low, heap.peek());
        assertEquals(2, heap.size());
    }

    @Test
    void poppedNodeLeavesTheOpenSet() {
        BinaryHeap heap = new BinaryHeap();
        Node n = heap.insert(node(0, 1));
        assertTrue(n.inOpenSet());
        heap.pop();
        assertFalse(n.inOpenSet());
    }

    @Test
    void insertingTwiceFails() {
        BinaryHeap heap = new BinaryHeap();
        Node n = heap.insert(node(0, 1));
        assertThrows(IllegalStateException.class, () -> heap.insert(n));
    }

    @Test
    void growsBeyondInitialCapacity() {
        BinaryHeap heap = new BinaryHeap();
        for (int i = 0; i < 1000; i++) heap.insert(node(i, 1000 - i));
        assertEquals(1000, heap.size());
        assertEquals(1f, heap.pop().f);
    }

    @Test
    void changeCostReordersBothWays() {
        BinaryHeap heap = new BinaryHeap();
        Node a = heap.insert(node(0, 10));
        Node b = heap.insert(node(1, 20));
        Node c = heap.insert(node(2, 30));
        heap.changeCost(c, 1);
        assertSame(c, heap.peek());
        heap.changeCost(c, 100);
        assertSame(a, heap.pop());
        assertSame(b, heap.pop());
        assertSame(c, heap.pop());
    }

    @Test
    void removeKeepsTheHeapValid() {
        BinaryHeap heap = new BinaryHeap();
        List<Node> nodes = new ArrayList<>();
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) nodes.add(heap.insert(node(i, random.nextInt(1000))));
        for (int i = 0; i < 200; i += 3) heap.remove(nodes.get(i));
        assertEquals(200 - 67, heap.size());
        float previous = -1;
        while (!heap.isEmpty()) {
            float f = heap.pop().f;
            assertTrue(f >= previous);
            previous = f;
        }
    }

    @Test
    void clearEmptiesTheHeap() {
        BinaryHeap heap = new BinaryHeap();
        heap.insert(node(0, 1));
        heap.clear();
        assertTrue(heap.isEmpty());
        assertEquals(0, heap.getHeap().length);
    }
}
