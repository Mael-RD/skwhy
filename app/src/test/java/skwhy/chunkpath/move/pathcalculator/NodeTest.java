package skwhy.chunkpath.move.pathcalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class NodeTest {

    @Test
    void heuristicIsZeroOnTargetAndOctileOnAxes() {
        Node a = new Node(0, 0, 0);
        assertEquals(0f, a.heuristic(new Node(0, 0, 0)), 1e-6f);
        assertEquals(5f, a.heuristic(new Node(5, 0, 0)), 1e-6f);
        assertEquals(5f, a.heuristic(new Node(0, -5, 0)), 1e-6f);
        // diagonale 2D : max + (sqrt2 - 1) * min
        assertEquals(3f + (float) (Math.sqrt(2) - 1) * 3f, a.heuristic(new Node(3, 0, 3)), 1e-5f);
        // diagonale 3D pure : 3 * (1 + sqrt3 - 2 + sqrt2 - 1) -> max + (sqrt3-2)min + (sqrt2-1)mid
        float d = 1f + (float) (Math.sqrt(3) - 2) + (float) (Math.sqrt(2) - 1);
        assertEquals(d, a.heuristic(new Node(1, 1, 1)), 1e-5f);
    }

    @Test
    void heuristicIsSymmetricAndNeverBelowStraightDistanceLowerBound() {
        Node a = new Node(1, 2, 3), b = new Node(-4, 7, 0);
        assertEquals(a.heuristic(b), b.heuristic(a), 1e-6f);
        assertTrue(a.heuristic(b) <= a.distanceManhattan(b));
        assertTrue(a.heuristic(b) >= a.distanceTo(b) * 0.5f);
    }

    @Test
    void distances() {
        Node a = new Node(0, 0, 0), b = new Node(3, 4, 12);
        assertEquals(13f, a.distanceTo(b), 1e-5f);
        assertEquals(169f, a.distanceToSqr(b), 1e-5f);
        assertEquals(19f, a.distanceManhattan(b), 1e-5f);
        assertEquals((float) Math.sqrt(9 + 144), a.distanceToXZ(b), 1e-5f);
    }

    @Test
    void equalsAndHashDependOnCoordinatesOnly() {
        Node a = new Node(10, 64, -5), b = new Node(10, 64, -5);
        a.g = 5;
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new Node(10, 65, -5));
        assertNotEquals(a, new Node(-10, 64, -5));
        assertNotEquals(a, new Node(10, 64, 5));
        assertFalse(a.equals("not a node"));
    }

    @Test
    void createHashSeparatesNegativeCoordinates() {
        assertNotEquals(Node.createHash(1, 0, 0), Node.createHash(-1, 0, 0));
        assertNotEquals(Node.createHash(0, 0, 1), Node.createHash(0, 0, -1));
    }

    @Test
    void cloneAndMoveCopiesTheSearchState() {
        Node a = new Node(1, 2, 3);
        Node parent = new Node(0, 0, 0);
        a.g = 1;
        a.h = 2;
        a.f = 3;
        a.cameFrom = parent;
        a.closed = true;
        a.walkedDistance = 4;
        a.costMalus = 5;
        a.type = PathType.WALKABLE;
        Node c = a.cloneAndMove(7, 8, 9);
        assertEquals(7, c.x);
        assertEquals(8, c.y);
        assertEquals(9, c.z);
        assertEquals(3f, c.f);
        assertSame(parent, c.cameFrom);
        assertTrue(c.closed);
        assertEquals(4f, c.walkedDistance);
        assertEquals(5f, c.costMalus);
        assertEquals(PathType.WALKABLE, c.type);
    }

    @Test
    void newNodeIsBlockedAndOutsideTheOpenSet() {
        Node n = new Node(0, 0, 0);
        assertEquals(PathType.BLOCKED, n.type);
        assertFalse(n.inOpenSet());
    }

    @Test
    void asVector() {
        assertEquals(new Vector(1, 2, 3), new Node(1, 2, 3).asVector());
    }
}
