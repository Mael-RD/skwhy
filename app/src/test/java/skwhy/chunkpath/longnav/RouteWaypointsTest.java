package skwhy.chunkpath.longnav;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import skwhy.chunkpath.Direction;
import skwhy.chunkpath.gate.Gate;

class RouteWaypointsTest {

    /** Le constructeur de Gate est reserve a son package : on le cree par reflexion. */
    private static Gate gate(int cx, int cz, int id, Direction edge, int[]... blocks) {
        try {
            Constructor<Gate> ctor = Gate.class.getDeclaredConstructor(int.class, int.class, int.class, Direction.class, List.class, int[].class);
            ctor.setAccessible(true);
            return ctor.newInstance(cx, cz, id, edge, new ArrayList<>(List.of(blocks)), blocks[0]);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void crossingPicksTheExitClosestToTheWalkAndItsAdjacentEntry() {
        Gate a = gate(0, 0, 0, Direction.EAST, new int[] {15, 1, 0}, new int[] {15, 1, 1}, new int[] {15, 1, 2});
        Gate b = gate(1, 0, 0, Direction.WEST, new int[] {16, 1, 0}, new int[] {16, 1, 1}, new int[] {16, 1, 2});
        RouteWaypoints w = RouteWaypoints.build(List.of(a, b), 10, 1, 2);
        assertEquals(2, w.size());
        assertArrayEquals(new int[] {15, 1, 2}, w.cell(0));
        assertArrayEquals(new int[] {16, 1, 2}, w.cell(1));
        assertSame(a, w.owner(0));
        assertSame(b, w.owner(1));
    }

    @Test
    void startingElsewhereChangesTheCrossingCell() {
        Gate a = gate(0, 0, 0, Direction.EAST, new int[] {15, 1, 0}, new int[] {15, 1, 1}, new int[] {15, 1, 2});
        Gate b = gate(1, 0, 0, Direction.WEST, new int[] {16, 1, 0}, new int[] {16, 1, 1}, new int[] {16, 1, 2});
        RouteWaypoints w = RouteWaypoints.build(List.of(a, b), 10, 1, 0);
        assertArrayEquals(new int[] {15, 1, 0}, w.cell(0));
        assertArrayEquals(new int[] {16, 1, 0}, w.cell(1));
    }

    @Test
    void entryAndExitOfAMiddleGateAreBothWaypoints() {
        Gate a = gate(0, 0, 0, Direction.EAST, new int[] {15, 1, 0});
        Gate b1 = gate(1, 0, 0, Direction.WEST, new int[] {16, 1, 0});
        Gate b2 = gate(1, 0, 1, Direction.EAST, new int[] {31, 1, 0});
        Gate c = gate(2, 0, 0, Direction.WEST, new int[] {32, 1, 0});
        RouteWaypoints w = RouteWaypoints.build(List.of(a, b1, b2, c), 0, 1, 0);
        assertEquals(4, w.size());
        assertArrayEquals(new int[] {15, 1, 0}, w.cell(0));
        assertArrayEquals(new int[] {16, 1, 0}, w.cell(1));
        assertArrayEquals(new int[] {31, 1, 0}, w.cell(2));
        assertArrayEquals(new int[] {32, 1, 0}, w.cell(3));
        assertSame(b2, w.owner(2));
        assertSame(c, w.owner(3));
    }

    @Test
    void singleGateWithoutCrossingUsesTheNearestMember() {
        Gate only = gate(0, 0, 0, Direction.NORTH, new int[] {0, 1, 0}, new int[] {7, 1, 0}, new int[] {14, 1, 0});
        RouteWaypoints w = RouteWaypoints.build(List.of(only), 13, 1, 5);
        assertEquals(1, w.size());
        assertArrayEquals(new int[] {14, 1, 0}, w.cell(0));
    }

    @Test
    void heightJumpsOfMoreThanOneBlockFallBackToRepresentatives() {
        Gate a = gate(0, 0, 0, Direction.EAST, new int[] {15, 1, 0});
        Gate b = gate(1, 0, 0, Direction.WEST, new int[] {16, 9, 0});
        RouteWaypoints w = RouteWaypoints.build(List.of(a, b), 0, 1, 0);
        assertEquals(2, w.size());
        assertArrayEquals(new int[] {15, 1, 0}, w.cell(0));
        assertArrayEquals(new int[] {16, 9, 0}, w.cell(1));
    }

    @Test
    void stepOfOneBlockIsAccepted() {
        Gate a = gate(0, 0, 0, Direction.EAST, new int[] {15, 4, 0}, new int[] {15, 9, 0});
        Gate b = gate(1, 0, 0, Direction.WEST, new int[] {16, 5, 0});
        RouteWaypoints w = RouteWaypoints.build(List.of(a, b), 0, 9, 0);
        assertArrayEquals(new int[] {15, 4, 0}, w.cell(0));
        assertArrayEquals(new int[] {16, 5, 0}, w.cell(1));
    }

    @Test
    void northSouthCrossing() {
        Gate a = gate(0, 0, 0, Direction.SOUTH, new int[] {2, 1, 15}, new int[] {3, 1, 15});
        Gate b = gate(0, 1, 0, Direction.NORTH, new int[] {2, 1, 16}, new int[] {3, 1, 16});
        RouteWaypoints w = RouteWaypoints.build(List.of(a, b), 3, 1, 5);
        assertArrayEquals(new int[] {3, 1, 15}, w.cell(0));
        assertArrayEquals(new int[] {3, 1, 16}, w.cell(1));
    }

    /** Border x = 15 | 16 over z = 0..8, cheap road on z = 3..5, expensive everywhere else. */
    private static Gate[] borderOfWidth9() {
        int[][] exit = new int[9][], entry = new int[9][];
        for (int z = 0; z < 9; z++) {
            exit[z] = new int[] {15, 1, z};
            entry[z] = new int[] {16, 1, z};
        }
        return new Gate[] {gate(0, 0, 0, Direction.EAST, exit), gate(1, 0, 0, Direction.WEST, entry)};
    }

    private static final RouteWaypoints.CellCost ROAD_ON_3_TO_5 = (x, y, z) -> z >= 3 && z <= 5 ? 1 : 200;

    @Test
    void refineMovesTheCrossingOntoTheCheapRoadInsteadOfTheClosestCell() {
        Gate[] g = borderOfWidth9();
        RouteWaypoints w = RouteWaypoints.build(List.of(g[0], g[1]), 10, 1, 8); // distances only: z = 8
        assertArrayEquals(new int[] {15, 1, 8}, w.cell(0));
        assertTrue(w.refine(0, new int[] {10, 1, 8}, new int[] {40, 1, 8}, ROAD_ON_3_TO_5));
        assertEquals(4, w.cell(0)[2], "the middle of the road (z 3..5) is wanted, not its edge");
        assertEquals(4, w.cell(1)[2]);
        assertEquals(15, w.cell(0)[0]);
        assertEquals(16, w.cell(1)[0]);
    }

    @Test
    void refineIsDoneOncePerWaypoint() {
        Gate[] g = borderOfWidth9();
        RouteWaypoints w = RouteWaypoints.build(List.of(g[0], g[1]), 10, 1, 8);
        assertTrue(w.refine(0, new int[] {10, 1, 8}, null, ROAD_ON_3_TO_5));
        assertFalse(w.refine(0, new int[] {10, 1, 0}, null, ROAD_ON_3_TO_5));
    }

    @Test
    void refineKeepsTheEarlierChoiceWhenTheTerrainIsNotLoaded() {
        Gate[] g = borderOfWidth9();
        RouteWaypoints w = RouteWaypoints.build(List.of(g[0], g[1]), 10, 1, 8);
        assertFalse(w.refine(0, new int[] {10, 1, 8}, null, (x, y, z) -> Integer.MIN_VALUE));
        assertArrayEquals(new int[] {15, 1, 8}, w.cell(0));
    }

    @Test
    void refineOnUniformGroundStaysAwayFromTheEndsOfTheBorder() {
        Gate[] g = borderOfWidth9();
        RouteWaypoints w = RouteWaypoints.build(List.of(g[0], g[1]), 10, 1, 0); // starts on z = 0: the end of the border
        w.refine(0, new int[] {10, 1, 0}, null, (x, y, z) -> 7);
        assertTrue(w.cell(0)[2] >= 2, "a crossing at the very end of a path is avoided, got z=" + w.cell(0)[2]);
    }

    @Test
    void refineDoesNotTouchAnEntryWaypoint() {
        Gate[] g = borderOfWidth9();
        RouteWaypoints w = RouteWaypoints.build(List.of(g[0], g[1]), 10, 1, 8);
        assertFalse(w.refine(1, new int[] {10, 1, 8}, null, ROAD_ON_3_TO_5));
    }

    @Test
    void refineAvoidsBlockedCells() {
        Gate[] g = borderOfWidth9();
        RouteWaypoints w = RouteWaypoints.build(List.of(g[0], g[1]), 10, 1, 4);
        // z 3..5 closed (wall / blocked block): the crossing must be elsewhere even if it is close
        w.refine(0, new int[] {10, 1, 4}, null, (x, y, z) -> z >= 3 && z <= 5 ? -1 : 5);
        assertTrue(w.cell(0)[2] < 3 || w.cell(0)[2] > 5);
    }
}
