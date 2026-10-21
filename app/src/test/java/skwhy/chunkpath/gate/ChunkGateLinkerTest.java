package skwhy.chunkpath.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import skwhy.chunkpath.Direction;

class ChunkGateLinkerTest {

    /** Porte sur un bord de chunk : blocs aux coordonnees monde donnees. */
    static Gate gate(int cx, int cz, int id, Direction edge, boolean deadEnd, int[]... blocks) {
        List<int[]> members = new ArrayList<>(List.of(blocks));
        Gate g = new Gate(cx, cz, id, edge, members, members.get(0));
        g.deadEndCandidate = deadEnd;
        return g;
    }

    @Test
    void globalIdContainsChunkAndGateId() {
        assertEquals("3,-2,7", gate(3, -2, 7, Direction.EAST, false, new int[] {1, 1, 1}).globalId());
    }

    @Test
    void facingGatesAreLinkedBothWays() {
        Gate a = gate(0, 0, 0, Direction.EAST, false, new int[] {15, 5, 3}, new int[] {15, 5, 4});
        Gate b = gate(1, 0, 0, Direction.WEST, false, new int[] {16, 5, 4}, new int[] {16, 5, 5});
        ChunkGateData chunkA = new ChunkGateData(0, 0, List.of(a));
        ChunkGateData chunkB = new ChunkGateData(1, 0, List.of(b));
        ChunkGateLinker.link(chunkA, chunkB, Direction.EAST);
        assertEquals(Set.of("1,0,0"), a.neighborLinks);
        assertEquals(Set.of("0,0,0"), b.neighborLinks);
    }

    @Test
    void differentHeightsAreNotLinked() {
        Gate a = gate(0, 0, 0, Direction.EAST, false, new int[] {15, 5, 3});
        Gate b = gate(1, 0, 0, Direction.WEST, false, new int[] {16, 9, 3});
        ChunkGateLinker.link(new ChunkGateData(0, 0, List.of(a)), new ChunkGateData(1, 0, List.of(b)), Direction.EAST);
        assertTrue(a.neighborLinks.isEmpty());
        assertTrue(b.neighborLinks.isEmpty());
    }

    @Test
    void differentLinesAreNotLinked() {
        Gate a = gate(0, 0, 0, Direction.EAST, false, new int[] {15, 5, 3});
        Gate b = gate(1, 0, 0, Direction.WEST, false, new int[] {16, 5, 8});
        ChunkGateLinker.link(new ChunkGateData(0, 0, List.of(a)), new ChunkGateData(1, 0, List.of(b)), Direction.EAST);
        assertTrue(a.neighborLinks.isEmpty());
    }

    @Test
    void northSouthUsesTheXAxis() {
        Gate a = gate(0, 0, 0, Direction.SOUTH, false, new int[] {4, 5, 15});
        Gate same = gate(0, 1, 0, Direction.NORTH, false, new int[] {4, 5, 16});
        Gate other = gate(0, 1, 1, Direction.NORTH, false, new int[] {9, 5, 16});
        ChunkGateLinker.link(new ChunkGateData(0, 0, List.of(a)), new ChunkGateData(0, 1, List.of(same, other)), Direction.SOUTH);
        assertEquals(Set.of("0,1,0"), a.neighborLinks);
        assertTrue(other.neighborLinks.isEmpty());
    }

    @Test
    void negativeCoordinatesAreLinked() {
        Gate a = gate(-1, 0, 0, Direction.EAST, false, new int[] {-1, 5, -3});
        Gate b = gate(0, 0, 0, Direction.WEST, false, new int[] {0, 5, -3});
        ChunkGateLinker.link(new ChunkGateData(-1, 0, List.of(a)), new ChunkGateData(0, 0, List.of(b)), Direction.EAST);
        assertEquals(Set.of("0,0,0"), a.neighborLinks);
    }

    @Test
    void nonAdjacentChunksAreRejected() {
        ChunkGateData a = new ChunkGateData(0, 0, List.of());
        assertThrows(IllegalArgumentException.class, () -> ChunkGateLinker.link(a, new ChunkGateData(2, 0, List.of()), Direction.EAST));
        assertThrows(IllegalArgumentException.class, () -> ChunkGateLinker.link(a, new ChunkGateData(1, 0, List.of()), Direction.WEST));
    }

    @Test
    void deadEndLeadingToASingleGateIsPruned() {
        Gate pocket = gate(0, 0, 0, Direction.EAST, true, new int[] {15, 5, 3});
        Gate other = gate(0, 0, 1, Direction.NORTH, false, new int[] {2, 5, 0});
        Gate across = gate(1, 0, 0, Direction.WEST, false, new int[] {16, 5, 3});
        ChunkGateData a = new ChunkGateData(0, 0, List.of(pocket, other));
        ChunkGateData b = new ChunkGateData(1, 0, List.of(across));
        ChunkGateLinker.link(a, b, Direction.EAST);
        assertNull(a.byId(0));
        assertNotNull(a.byId(1));
        assertEquals(1, a.gates().size());
        assertTrue(a.onEdge(Direction.EAST).isEmpty());
    }

    @Test
    void deadEndRevealedAsCrossroadsIsKept() {
        Gate pocket = gate(0, 0, 0, Direction.EAST, true, new int[] {15, 5, 3}, new int[] {15, 5, 9});
        Gate across1 = gate(1, 0, 0, Direction.WEST, false, new int[] {16, 5, 3});
        Gate across2 = gate(1, 0, 1, Direction.WEST, false, new int[] {16, 5, 9});
        ChunkGateData a = new ChunkGateData(0, 0, List.of(pocket));
        ChunkGateData b = new ChunkGateData(1, 0, List.of(across1, across2));
        ChunkGateLinker.link(a, b, Direction.EAST);
        assertNotNull(a.byId(0));
        assertFalse(pocket.deadEndCandidate);
        assertEquals(Set.of("1,0,0", "1,0,1"), pocket.neighborLinks);
    }

    @Test
    void nonDeadEndGatesAreNeverPruned() {
        Gate a1 = gate(0, 0, 0, Direction.EAST, false, new int[] {15, 5, 3});
        Gate b1 = gate(1, 0, 0, Direction.WEST, false, new int[] {16, 5, 3});
        ChunkGateData a = new ChunkGateData(0, 0, List.of(a1));
        ChunkGateData b = new ChunkGateData(1, 0, List.of(b1));
        ChunkGateLinker.link(a, b, Direction.EAST);
        assertEquals(1, a.gates().size());
        assertEquals(1, b.gates().size());
    }

    @Test
    void chunkGateDataIndexesGatesByEdge() {
        Gate n = gate(0, 0, 0, Direction.NORTH, false, new int[] {1, 1, 0});
        Gate e1 = gate(0, 0, 1, Direction.EAST, false, new int[] {15, 1, 1});
        Gate e2 = gate(0, 0, 2, Direction.EAST, false, new int[] {15, 1, 9});
        ChunkGateData data = new ChunkGateData(0, 0, List.of(n, e1, e2));
        assertEquals(1, data.onEdge(Direction.NORTH).size());
        assertEquals(2, data.onEdge(Direction.EAST).size());
        assertTrue(data.onEdge(Direction.SOUTH).isEmpty());
        assertEquals(e2, data.byId(2));
        assertNull(data.byId(42));
        data.prune(Set.of());
        assertEquals(3, data.gates().size());
        data.prune(Set.of(1));
        assertEquals(1, data.onEdge(Direction.EAST).size());
    }
}
