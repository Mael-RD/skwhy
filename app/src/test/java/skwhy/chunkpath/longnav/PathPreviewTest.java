package skwhy.chunkpath.longnav;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PathPreviewTest {

    /** Straight walk along x then z, one block at a time, to the waypoint asked for; counts the legs it was asked for. */
    private static class Straight implements PathPreview.LegSolver {
        final List<int[][]> calls = new ArrayList<>();
        final List<int[]> targets;
        int restarts;
        boolean blocked;

        Straight(List<int[]> targets) {
            this.targets = targets;
        }

        @Override
        public void restart() {
            restarts++;
        }

        @Override
        public PathPreview.Leg leg(int[] from, int target) {
            calls.add(new int[][] { from.clone(), targets.get(target).clone() });
            if (blocked) return null;
            return new PathPreview.Leg(walk(from, targets.get(target)), target + 1);
        }

        @Override
        public PathPreview.Leg back(int[] from, int[] to) {
            calls.add(new int[][] { from.clone(), to.clone() });
            return new PathPreview.Leg(walk(from, to), 0);
        }

        static List<int[]> walk(int[] from, int[] to) {
            List<int[]> cells = new ArrayList<>();
            int x = from[0], z = from[2];
            cells.add(new int[] { x, from[1], z });
            while (x != to[0]) { x += Integer.signum(to[0] - x); cells.add(new int[] { x, to[1], z }); }
            while (z != to[2]) { z += Integer.signum(to[2] - z); cells.add(new int[] { x, to[1], z }); }
            return cells;
        }
    }

    private static List<int[]> targets(int[]... cells) {
        return new ArrayList<>(List.of(cells));
    }

    private static String describe(List<int[]> cells) {
        StringBuilder sb = new StringBuilder();
        for (int[] c : cells) sb.append(c[0]).append(',').append(c[2]).append(' ');
        return sb.toString().trim();
    }

    @Test
    void returnsTheCellsAfterTheStartingPosition() {
        PathPreview preview = new PathPreview(new Straight(targets(new int[] { 3, 1, 0 })));
        List<int[]> cells = preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 1, 0);
        assertEquals("1,0 2,0 3,0", describe(cells));
        assertTrue(preview.isComplete());
    }

    @Test
    void askingTwiceFromTheSamePositionGivesTheSameCellsWithoutSearching() {
        Straight solver = new Straight(targets(new int[] { 5, 1, 0 }, new int[] { 5, 1, 5 }));
        PathPreview preview = new PathPreview(solver);
        String first = describe(preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 2, 0));
        solver.calls.clear();
        // the first cell is one block from the position: it is ahead of it, not behind
        assertEquals(first, describe(preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 2, 0)));
        assertEquals(first, describe(preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 2, 0)));
        assertTrue(solver.calls.isEmpty());
        assertEquals("1,0 2,0", describe(preview.next(new int[] { 0, 1, 0 }, 2, 2, 0)));
        assertEquals(1, solver.restarts, "the walk was started once");
    }

    @Test
    void limitStopsTheSearchOnceEnoughCellsAreKnown() {
        Straight solver = new Straight(targets(new int[] { 5, 1, 0 }, new int[] { 5, 1, 5 }, new int[] { 9, 1, 5 }));
        PathPreview preview = new PathPreview(solver);
        List<int[]> cells = preview.next(new int[] { 0, 1, 0 }, 3, 3, 0);
        assertEquals("1,0 2,0 3,0", describe(cells));
        assertEquals(1, solver.calls.size(), "only the first leg was searched");
        assertFalse(preview.isComplete());
    }

    @Test
    void askingForMoreOnlySearchesTheMissingLegs() {
        Straight solver = new Straight(targets(new int[] { 5, 1, 0 }, new int[] { 5, 1, 5 }, new int[] { 9, 1, 5 }));
        PathPreview preview = new PathPreview(solver);
        preview.next(new int[] { 0, 1, 0 }, 3, 3, 0);
        solver.calls.clear();
        List<int[]> cells = preview.next(new int[] { 0, 1, 0 }, 7, 3, 0);
        assertEquals(7, cells.size());
        assertEquals(1, solver.calls.size(), "only the leg after the first one");
        assertEquals("5,0", solver.calls.get(0)[0][0] + "," + solver.calls.get(0)[0][2], "it starts where the known cells end");
        assertEquals("5,5", solver.calls.get(0)[1][0] + "," + solver.calls.get(0)[1][2]);
        assertEquals(1, solver.restarts, "asking for more does not start the walk again");
    }

    @Test
    void movingAlongThePathSearchesNothingAndDropsPassedCells() {
        Straight solver = new Straight(targets(new int[] { 6, 1, 0 }));
        PathPreview preview = new PathPreview(solver);
        preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 1, 0);
        solver.calls.clear();
        List<int[]> cells = preview.next(new int[] { 3, 1, 0 }, Integer.MAX_VALUE, 1, 0);
        assertEquals("4,0 5,0 6,0", describe(cells));
        assertTrue(solver.calls.isEmpty());
    }

    @Test
    void aPositionNextToThePathCountsAsBeingOnIt() {
        Straight solver = new Straight(targets(new int[] { 6, 1, 0 }));
        PathPreview preview = new PathPreview(solver);
        preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 1, 0);
        solver.calls.clear();
        List<int[]> cells = preview.next(new int[] { 3, 1, 1 }, Integer.MAX_VALUE, 1, 0);
        assertEquals("4,0 5,0 6,0", describe(cells));
        assertTrue(solver.calls.isEmpty());
    }

    @Test
    void aShortWayOffIsBroughtBackOntoTheKeptCellsKeepingTheRestOfThePath() {
        Straight solver = new Straight(targets(new int[] { 20, 1, 0 }));
        PathPreview preview = new PathPreview(solver);
        preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 1, 0);
        solver.calls.clear();
        List<int[]> cells = preview.next(new int[] { 8, 1, 6 }, Integer.MAX_VALUE, 1, 0);
        assertEquals(3, solver.calls.size(), "the nearest cell, one in between and the furthest within reach were tried");
        assertEquals("8,0", solver.calls.get(0)[1][0] + "," + solver.calls.get(0)[1][2], "the nearest kept cell comes first");
        assertEquals("20,0", solver.calls.get(2)[1][0] + "," + solver.calls.get(2)[1][2]);
        // every way costs the same here: the walk makes progress instead of turning back
        assertEquals(18, cells.size());
        assertEquals("20,0", cells.get(17)[0] + "," + cells.get(17)[2]);
        assertEquals(1, solver.restarts, "the kept cells are still used: the walk is not started again");
    }

    @Test
    void farFromTheKeptCellsTheSearchStartsAgainFromTheGivenWaypoint() {
        Straight solver = new Straight(targets(new int[] { 5, 1, 0 }, new int[] { 5, 1, 50 }, new int[] { 40, 1, 50 }));
        PathPreview preview = new PathPreview(solver);
        preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 3, 0);
        solver.calls.clear();
        // 40 blocks from every kept cell, in the chunk of the last leg: the first waypoint is the third one, the first two
        // are not searched again
        List<int[]> cells = preview.next(new int[] { 30, 1, 90 }, Integer.MAX_VALUE, 3, 2);
        assertEquals(1, solver.calls.size());
        assertEquals("30,90", solver.calls.get(0)[0][0] + "," + solver.calls.get(0)[0][2]);
        assertEquals("40,50", solver.calls.get(0)[1][0] + "," + solver.calls.get(0)[1][2]);
        assertEquals(50, cells.size());
        assertEquals(2, solver.restarts, "what the solver chose during the first walk is forgotten");
    }

    @Test
    void whenEveryKeptCellIsPassedAndTheEntityLeavesItIsSearchedAgain() {
        Straight solver = new Straight(targets(new int[] { 3, 1, 0 }));
        PathPreview preview = new PathPreview(solver);
        preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 1, 0);
        assertTrue(preview.next(new int[] { 3, 1, 0 }, Integer.MAX_VALUE, 1, 0).isEmpty(), "arrived");
        solver.calls.clear();
        List<int[]> cells = preview.next(new int[] { 0, 1, 20 }, Integer.MAX_VALUE, 1, 0);
        assertEquals(1, solver.calls.size());
        assertEquals(23, cells.size());
    }

    @Test
    void anUnsolvableLegEndsTheListAndIsRetriedAtTheNextCall() {
        Straight solver = new Straight(targets(new int[] { 3, 1, 0 }, new int[] { 3, 1, 4 }));
        PathPreview preview = new PathPreview(solver);
        // first leg ok, second one cannot be computed yet (e.g. the chunk is not available)
        Straight partialSolver = new Straight(solver.targets) {
            @Override
            public PathPreview.Leg leg(int[] from, int target) {
                return target == 1 ? null : super.leg(from, target);
            }
        };
        PathPreview partial = new PathPreview(partialSolver);
        List<int[]> cells = partial.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 2, 0);
        assertEquals("1,0 2,0 3,0", describe(cells));
        assertFalse(partial.isComplete());

        // nothing is cached for a leg that failed: a solver that now works completes the list
        solver.blocked = true;
        assertEquals(0, preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 2, 0).size());
        solver.blocked = false;
        assertEquals(7, preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 2, 0).size());
        assertTrue(preview.isComplete());
    }

    @Test
    void aLegThatSkipsWaypointsMakesTheNextOneStartAfterThem() {
        // the first leg goes straight to the last waypoint (a shortcut): the waypoint in between is not searched
        List<int[]> waypoints = targets(new int[] { 5, 1, 0 }, new int[] { 5, 1, 5 }, new int[] { 9, 1, 5 });
        List<int[][]> calls = new ArrayList<>();
        PathPreview preview = new PathPreview(new PathPreview.LegSolver() {
            @Override public void restart() { }
            @Override public PathPreview.Leg leg(int[] from, int target) {
                calls.add(new int[][] { from.clone(), waypoints.get(target).clone() });
                int reached = target == 0 ? 2 : target;
                return new PathPreview.Leg(Straight.walk(from, waypoints.get(reached)), reached + 1);
            }
            @Override public PathPreview.Leg back(int[] from, int[] to) { return null; }
        });
        List<int[]> cells = preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 3, 0);
        assertEquals(1, calls.size(), "one search for the whole walk");
        assertEquals(14, cells.size());
        assertTrue(preview.isComplete());
    }

    @Test
    void aLegCutShortIsContinuedByASearchFromWhereItStopped() {
        // the walk is on the waypoint's chunk before the end of the path: the next leg heads for the same waypoint again
        List<int[]> waypoints = targets(new int[] { 6, 1, 0 });
        List<int[][]> calls = new ArrayList<>();
        PathPreview preview = new PathPreview(new PathPreview.LegSolver() {
            @Override public void restart() { }
            @Override public PathPreview.Leg leg(int[] from, int target) {
                calls.add(new int[][] { from.clone(), waypoints.get(target).clone() });
                if (calls.size() == 1) return new PathPreview.Leg(Straight.walk(from, new int[] { 3, 1, 0 }), target);
                return new PathPreview.Leg(Straight.walk(from, waypoints.get(target)), target + 1);
            }
            @Override public PathPreview.Leg back(int[] from, int[] to) { return null; }
        });
        List<int[]> cells = preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 1, 0);
        assertEquals(2, calls.size());
        assertEquals("3,0", calls.get(1)[0][0] + "," + calls.get(1)[0][2], "the second search starts where the first one stopped");
        assertEquals("1,0 2,0 3,0 4,0 5,0 6,0", describe(cells));
    }

    @Test
    void aLegWithoutProgressEndsTheListInsteadOfLooping() {
        PathPreview preview = new PathPreview(new PathPreview.LegSolver() {
            @Override public void restart() { }
            @Override public PathPreview.Leg leg(int[] from, int target) {
                return new PathPreview.Leg(new ArrayList<>(List.of(from)), target);
            }
            @Override public PathPreview.Leg back(int[] from, int[] to) { return null; }
        });
        assertTrue(preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 2, 0).isEmpty());
        assertFalse(preview.isComplete());
    }

    @Test
    void aWalkAlreadyAtTheDestinationIsCompleteWithoutCells() {
        // the solver says every waypoint is already reached: next goes past the last one
        PathPreview preview = new PathPreview(new PathPreview.LegSolver() {
            @Override public void restart() { }
            @Override public PathPreview.Leg leg(int[] from, int target) {
                return new PathPreview.Leg(new ArrayList<>(List.of(from)), 2);
            }
            @Override public PathPreview.Leg back(int[] from, int[] to) { return null; }
        });
        assertTrue(preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 2, 0).isEmpty());
        assertTrue(preview.isComplete());
    }

    @Test
    void invalidateForgetsEverything() {
        Straight solver = new Straight(targets(new int[] { 4, 1, 0 }));
        PathPreview preview = new PathPreview(solver);
        preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 1, 0);
        preview.invalidate();
        assertEquals(0, preview.size());
        solver.calls.clear();
        assertEquals(4, preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 1, 0).size());
        assertEquals(1, solver.calls.size());
        assertEquals(2, solver.restarts);
    }

    @Test
    void zeroLimitOrNoTargetGivesAnEmptyListWithoutSearching() {
        Straight solver = new Straight(targets(new int[] { 3, 1, 0 }));
        PathPreview preview = new PathPreview(solver);
        assertTrue(preview.next(new int[] { 0, 1, 0 }, 0, 1, 0).isEmpty());
        assertTrue(preview.next(new int[] { 0, 1, 0 }, 5, 0, 0).isEmpty());
        assertTrue(solver.calls.isEmpty());
    }

    @Test
    void expandFillsTheCellsBetweenFarNodes() {
        List<int[]> cells = PathPreview.expand(List.of(new int[] { 0, 5, 0 }, new int[] { 4, 5, 0 }, new int[] { 5, 6, 0 }));
        assertEquals("0,0 1,0 2,0 3,0 4,0 5,0", describe(cells));
        assertEquals(6, cells.get(5)[1], "the step up is on the last cell");
        assertEquals(5, cells.get(2)[1]);
    }

    @Test
    void expandKeepsADiagonalContinuous() {
        List<int[]> cells = PathPreview.expand(List.of(new int[] { 0, 1, 0 }, new int[] { 4, 1, 2 }));
        assertEquals(5, cells.size());
        for (int i = 1; i < cells.size(); i++) {
            assertTrue(Math.abs(cells.get(i)[0] - cells.get(i - 1)[0]) <= 1);
            assertTrue(Math.abs(cells.get(i)[2] - cells.get(i - 1)[2]) <= 1);
        }
        assertEquals(4, cells.get(4)[0]);
        assertEquals(2, cells.get(4)[2]);
    }

    @Test
    void expandOfASingleNodeIsThatNode() {
        assertEquals(1, PathPreview.expand(List.of(new int[] { 7, 2, 7 })).size());
    }

    /** Straight walks whose way back onto the path costs what a function of the cell joined says (the path itself: 1 per cell). */
    private static final class Priced extends Straight {
        final java.util.function.ToDoubleFunction<int[]> backCost;
        final List<int[]> joined = new ArrayList<>();

        Priced(List<int[]> targets, java.util.function.ToDoubleFunction<int[]> backCost) {
            super(targets);
            this.backCost = backCost;
        }

        @Override
        public PathPreview.Leg back(int[] from, int[] to) {
            joined.add(to.clone());
            List<int[]> cells = walk(from, to);
            double[] costs = new double[cells.size()];
            costs[cells.size() - 1] = backCost.applyAsDouble(to);
            return new PathPreview.Leg(cells, 0, costs);
        }
    }

    @Test
    void aWayBackThatIsExpensiveToTheNearestCellJoinsACellFurtherAlong() {
        // the path runs along x at z=0; the position is 6 blocks off it at x=8: joining x=8 crosses bad ground (cost 500), joining
        // a cell further along is free of it
        Priced solver = new Priced(targets(new int[] { 20, 1, 0 }), to -> to[0] <= 8 ? 500.0 : 7.0);
        PathPreview preview = new PathPreview(solver);
        preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 1, 0);
        List<int[]> cells = preview.next(new int[] { 8, 1, 6 }, Integer.MAX_VALUE, 1, 0);
        assertTrue(solver.joined.size() >= 2, "several cells were tried");
        int[] first = cells.get(cells.size() - 1 - (20 - solver.joined.get(solver.joined.size() - 1)[0]));
        assertEquals(solver.joined.get(solver.joined.size() - 1)[0], first[0], "the walk joins the furthest cell tried");
        assertTrue(solver.joined.get(solver.joined.size() - 1)[0] > 8);
        assertEquals("20,0", cells.get(cells.size() - 1)[0] + "," + cells.get(cells.size() - 1)[2], "the path is kept to its end");
    }

    @Test
    void aWayBackThatCostsTheSameEverywhereJoinsTheNearestCellsNeighbourhoodNotMuchFurther() {
        // joining further along costs 3% more: progress wins (within the bias); 30% more does not
        Priced cheap = new Priced(targets(new int[] { 20, 1, 0 }), to -> 100.0 + (to[0] - 8) * 0.0);
        PathPreview a = new PathPreview(cheap);
        a.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 1, 0);
        a.next(new int[] { 8, 1, 6 }, Integer.MAX_VALUE, 1, 0);
        // total = back + what is left of the path: the furthest cell leaves the least path, so it is the cheapest
        int[] last = cheap.joined.get(cheap.joined.size() - 1);
        assertTrue(last[0] > 8);

        Priced dear = new Priced(targets(new int[] { 20, 1, 0 }), to -> 100.0 + (to[0] - 8) * 40.0);
        PathPreview b = new PathPreview(dear);
        b.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 1, 0);
        List<int[]> cells = b.next(new int[] { 8, 1, 6 }, Integer.MAX_VALUE, 1, 0);
        // the way back to a further cell costs 40 more per block, the path saved 1 per block: the nearest cell is kept
        assertEquals("8,5", cells.get(0)[0] + "," + cells.get(0)[2]);
        assertEquals("8,0", cells.get(5)[0] + "," + cells.get(5)[2]);
    }

    @Test
    void whenNoWayBackIsFoundTheSearchStartsAgain() {
        Straight solver = new Straight(targets(new int[] { 20, 1, 0 })) {
            @Override
            public PathPreview.Leg back(int[] from, int[] to) {
                return null;
            }
        };
        PathPreview preview = new PathPreview(solver);
        preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 1, 0);
        List<int[]> cells = preview.next(new int[] { 8, 1, 6 }, Integer.MAX_VALUE, 1, 0);
        assertEquals(2, solver.restarts);
        assertEquals(12 + 6, cells.size());
    }

    @Test
    void expandGivesTheCostUpToEachCell() {
        PathPreview.Leg leg = PathPreview.expand(List.of(new int[] { 0, 5, 0 }, new int[] { 4, 5, 0 }, new int[] { 5, 5, 0 }), new double[] { 10, 50, 70 });
        assertEquals(6, leg.cells().size());
        assertEquals(0.0, leg.costAt(0), 1e-9);
        assertEquals(10.0, leg.costAt(1), 1e-9);
        assertEquals(40.0, leg.costAt(4), 1e-9);
        assertEquals(60.0, leg.costAt(5), 1e-9);
        assertEquals(60.0, leg.cost(), 1e-9);
    }

    @Test
    void provisionalLegIsSearchedAgainOnceTheTerrainIsLoaded() {
        boolean[] loaded = { false };
        Straight solver = new Straight(targets(new int[] { 3, 1, 0 })) {
            @Override
            public PathPreview.Leg leg(int[] from, int target) {
                PathPreview.Leg leg = super.leg(from, target);
                return leg != null && !loaded[0] ? leg.asProvisional() : leg;
            }

            @Override
            public boolean canRefine(int[] from) {
                return loaded[0];
            }
        };
        PathPreview preview = new PathPreview(solver);
        preview.next(new int[] { 0, 1, 0 }, Integer.MAX_VALUE, 1, 0);
        assertEquals(1, solver.calls.size());
        preview.next(new int[] { 1, 1, 0 }, Integer.MAX_VALUE, 1, 0); // still unloaded: kept as is
        assertEquals(1, solver.calls.size());
        loaded[0] = true;
        List<int[]> cells = preview.next(new int[] { 1, 1, 0 }, Integer.MAX_VALUE, 1, 0);
        assertEquals(2, solver.calls.size());
        assertEquals("2,0 3,0", describe(cells));
    }
}
