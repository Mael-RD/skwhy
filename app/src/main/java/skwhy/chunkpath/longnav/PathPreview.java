package skwhy.chunkpath.longnav;

import java.util.ArrayList;
import java.util.List;

/**
 * Cell-by-cell path of a long navigation, computed on demand and kept between two calls.
 *
 * <p>The route (gates, then {@link RouteWaypoints}) only says which cells to pass through; the cells in between come from a
 * {@link LegSolver}, which plays the walk's own decisions (which waypoint a leg heads for, shortcuts, crossings chosen again...).
 * Asking again from a new position reuses what is still valid instead of searching again:
 * <ul>
 *   <li>the position is on the cells kept (or next to one): the cells already passed are dropped, nothing is searched;</li>
 *   <li>a short way off: the cheapest way back onto the kept cells is chosen among a few of them (the nearest, one further
 *       along, the furthest within reach): joining a cell further along often costs less, or hardly more, when the nearest
 *       one is across expensive ground; the rest of the kept cells is kept;</li>
 *   <li>elsewhere: the cells are searched again from the position, from the given waypoint (the caller knows which
 *       gates of the route are still ahead, so the gates already found are not searched again);</li>
 *   <li>a longer list than the one kept: only the missing legs are searched, from the end of the kept cells.</li>
 * </ul>
 *
 * <p>Cells are {@code {x, y, z}} block coordinates (the cell the feet are in).
 */
final class PathPreview {

    /**
     * The cells of one leg ({@code cells[0]} is where it starts), the waypoint index the walk heads for once it is over and the
     * cost of the walk up to each cell ({@code costs[0]} = 0; null = one per cell, when the leg has no costs of its own).
     */
    record Leg(List<int[]> cells, int next, double[] costs, boolean provisional) {
        Leg(List<int[]> cells, int next, double[] costs) {
            this(cells, next, costs, false);
        }

        Leg(List<int[]> cells, int next) {
            this(cells, next, null, false);
        }

        /** The same leg flagged as stand-in (read from a region file: no smoothing, no centring), to be searched again once the terrain is loaded. */
        Leg asProvisional() {
            return new Leg(cells, next, costs, true);
        }

        /** Cost of the whole leg. */
        double cost() {
            if (cells.isEmpty()) return 0.0;
            return costs != null ? costs[cells.size() - 1] : cells.size() - 1;
        }

        double costAt(int index) {
            return costs != null ? costs[index] : index;
        }
    }

    interface LegSolver {
        /** A new walk starts (nothing kept): the solver forgets what it chose during the previous one. */
        void restart();

        /**
         * The next leg of the walk standing on {@code from} and heading for waypoint {@code target}, or null when it cannot be
         * computed (now). {@link Leg#next()} is greater than {@code target} when the leg went past it (shortcut, waypoint already
         * reached) and equal to it when the leg was cut short (the walk is on the waypoint's chunk before the end of the path).
         */
        Leg leg(int[] from, int target);

        /** The small leg from a position back onto a kept cell (both included; {@code next} is not used), or null. */
        Leg back(int[] from, int[] to);

        /** True when a provisional leg starting on {@code from} can now be searched for real (the terrain around it is loaded). */
        default boolean canRefine(int[] from) {
            return false;
        }
    }

    /** Squared distance (blocks) under which a position counts as being on a kept cell. */
    static final int ON_PATH_SQUARED = 2;
    /** Distance (blocks) up to which a position off the kept cells is brought back onto them instead of searching it all again. */
    static final double REJOIN_MAX = 16.0;
    /** A cell further along wins the rejoin when its total cost is at most this much above the best one (progress is worth a little). */
    static final double ADVANCE_BIAS = 1.05;

    /** A kept cell: where, the waypoint index the walk heads for there, and the cost of the walk up to it (only differences count). */
    private static final class Kept {
        final int[] cell; // {x, y, z, waypoint index}
        final double acc;
        /** Comes from a provisional leg (see {@link Leg#provisional()}). */
        final boolean provisional;

        Kept(int x, int y, int z, int target, double acc) {
            this(x, y, z, target, acc, false);
        }

        Kept(int x, int y, int z, int target, double acc, boolean provisional) {
            this.cell = new int[] { x, y, z, target };
            this.acc = acc;
            this.provisional = provisional;
        }
    }

    private final LegSolver solver;
    /** The first one is the cell the walk is on (where the entity was last asked from), the others are the cells ahead of it. */
    private final List<Kept> cells = new ArrayList<>();
    /** Waypoint index the walk heads for after the last kept cell (where the next leg is searched). */
    private int tail = -1;
    private boolean complete;

    PathPreview(LegSolver solver) {
        this.solver = solver;
    }

    /** Forgets everything (the route, the rules or the destination changed). */
    void invalidate() {
        cells.clear();
        tail = -1;
        complete = false;
    }

    /** True when the kept cells reach the destination. */
    boolean isComplete() {
        return complete;
    }

    /** Number of cells kept, the one the walk is on included (for tests). */
    int size() {
        return cells.size();
    }

    /**
     * The cells ahead of {@code here}, nearest first (the cell {@code here} is on is not part of them).
     *
     * @param limit       how many cells at most ({@link Integer#MAX_VALUE} = all of them)
     * @param targetCount number of waypoints, the destination included (the destination is the last index)
     * @param firstTarget index of the first waypoint still ahead of {@code here}, used when the kept cells cannot be reused
     */
    List<int[]> next(int[] here, int limit, int targetCount, int firstTarget) {
        List<int[]> result = new ArrayList<>();
        if (limit <= 0 || targetCount <= 0) return result;

        if (!cells.isEmpty()) locate(here);
        if (cells.isEmpty()) { // nothing usable kept: the walk starts here, toward the first waypoint still ahead
            solver.restart();
            tail = firstTarget;
            complete = false;
            cells.add(new Kept(here[0], here[1], here[2], tail, 0.0));
        }

        dropRefinableStandIns();

        while (cells.size() - 1 < limit && !complete) {
            if (tail >= targetCount) {
                complete = true;
                break;
            }
            Kept last = cells.get(cells.size() - 1);
            Leg leg = solver.leg(last.cell, tail);
            if (leg == null) break; // not computable now (terrain not available): what is known is returned
            if (leg.next() <= tail && leg.cells().size() <= 1) break; // no progress: never loop
            for (int i = 1; i < leg.cells().size(); i++) {
                int[] cell = leg.cells().get(i);
                cells.add(new Kept(cell[0], cell[1], cell[2], tail, last.acc + leg.costAt(i), leg.provisional()));
            }
            tail = leg.next();
            if (tail >= targetCount) complete = true;
        }

        for (int i = 1; i < cells.size() && i <= limit; i++) {
            int[] cell = cells.get(i).cell;
            result.add(new int[] { cell[0], cell[1], cell[2], cell[3] });
        }
        return result;
    }

    /**
     * The cells of a provisional leg were read from a region file; once the walk gets near enough for the terrain to be loaded they
     * are forgotten from the leg's start on, so the leg is searched again like a real walk would (smoothed, centred).
     */
    private void dropRefinableStandIns() {
        for (int i = 1; i < cells.size(); i++) {
            if (!cells.get(i).provisional) continue;
            int start = i - 1; // the cell the leg started from
            while (start > 0 && cells.get(start).provisional) start--;
            if (!solver.canRefine(cells.get(start).cell)) return;
            tail = cells.get(start + 1).cell[3];
            complete = false;
            cells.subList(start + 1, cells.size()).clear();
            return;
        }
    }

    /** Makes the cell the position is on the first one (what was passed is dropped), or brings the position back onto the kept cells, or forgets them. */
    private void locate(int[] here) {
        int nearest = 0;
        long nearestSquared = Long.MAX_VALUE;
        for (int i = 0; i < cells.size(); i++) {
            long squared = squaredDistance(cells.get(i).cell, here);
            if (squared < nearestSquared) {
                nearestSquared = squared;
                nearest = i;
            }
        }
        if (nearestSquared <= ON_PATH_SQUARED) {
            cells.subList(0, nearest).clear();
            return;
        }
        if (Math.sqrt(nearestSquared) <= REJOIN_MAX) {
            rejoin(here, nearest);
            if (!cells.isEmpty()) return;
        }
        invalidate();
    }

    /**
     * Brings a position a short way off back onto the kept cells at the cheapest of a few cells: the nearest, the furthest within
     * reach and the one in between. What it costs is the way back plus what is left of the kept cells from there.
     */
    private void rejoin(int[] here, int nearest) {
        int far = nearest;
        for (int i = nearest + 1; i < cells.size(); i++) {
            if (Math.sqrt(squaredDistance(cells.get(i).cell, here)) <= REJOIN_MAX) far = i;
        }
        int[] candidates = far == nearest ? new int[] { nearest }
                : far - nearest == 1 ? new int[] { nearest, far }
                : new int[] { nearest, (nearest + far) / 2, far };
        double lastAcc = cells.get(cells.size() - 1).acc;
        Leg best = null;
        int bestIndex = -1;
        double bestTotal = Double.MAX_VALUE;
        for (int index : candidates) {
            Leg back = solver.back(here, cells.get(index).cell);
            if (back == null || back.cells().isEmpty()) continue;
            double total = back.cost() + (lastAcc - cells.get(index).acc);
            // a cell further along wins on a tie, and when it costs hardly more (the walk makes progress instead of turning back)
            if (best == null || total <= bestTotal * ADVANCE_BIAS) {
                if (best == null || total < bestTotal || index > bestIndex) {
                    best = back;
                    bestIndex = index;
                    bestTotal = Math.min(total, bestTotal);
                }
            }
        }
        if (best == null) {
            cells.clear();
            return;
        }
        Kept joined = cells.get(bestIndex);
        int target = joined.cell[3];
        double start = joined.acc - best.cost();
        List<Kept> rest = new ArrayList<>(cells.subList(bestIndex + 1, cells.size()));
        cells.clear();
        cells.add(new Kept(here[0], here[1], here[2], target, start));
        for (int i = 1; i < best.cells().size(); i++) {
            int[] cell = best.cells().get(i);
            cells.add(new Kept(cell[0], cell[1], cell[2], target, start + best.costAt(i), best.provisional()));
        }
        cells.addAll(rest);
    }

    private static long squaredDistance(int[] a, int[] b) {
        long dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Every cell crossed by a path whose nodes may be several blocks apart (a smoothed path jumps over the nodes of a straight
     * stretch): between two nodes the cells are those of the straight line, at the level of the first node until the last step.
     */
    static List<int[]> expand(List<int[]> nodes) {
        return expand(nodes, null).cells();
    }

    /** The cells of {@link #expand(List)} with the cost of the walk up to each one, from the cost at each node ({@code g}, null = none). */
    static Leg expand(List<int[]> nodes, double[] g) {
        List<int[]> cells = new ArrayList<>();
        List<Double> costs = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            int[] b = nodes.get(i);
            double gb = g == null ? i : g[i] - g[0];
            if (i == 0) {
                cells.add(new int[] { b[0], b[1], b[2] });
                costs.add(0.0);
                continue;
            }
            int[] a = nodes.get(i - 1);
            double ga = g == null ? i - 1 : g[i - 1] - g[0];
            int dx = b[0] - a[0], dz = b[2] - a[2];
            int steps = Math.max(Math.abs(dx), Math.abs(dz));
            if (steps <= 1) {
                cells.add(new int[] { b[0], b[1], b[2] });
                costs.add(gb);
                continue;
            }
            for (int k = 1; k <= steps; k++) {
                int x = a[0] + Math.round((float) dx * k / steps);
                int z = a[2] + Math.round((float) dz * k / steps);
                cells.add(new int[] { x, k == steps ? b[1] : a[1], z });
                costs.add(ga + (gb - ga) * k / steps);
            }
        }
        double[] array = new double[costs.size()];
        for (int i = 0; i < array.length; i++) array[i] = costs.get(i);
        return new Leg(cells, 0, array);
    }
}
