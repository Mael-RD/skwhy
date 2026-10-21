package skwhy.chunkpath.longnav;

import skwhy.chunkpath.Direction;
import skwhy.chunkpath.gate.Gate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Concrete blocks to walk through, derived from the coarse gate route.
 *
 * <p>A gate is a whole cluster of border blocks (cheap-to-merge cells, possibly many blocks apart and
 * even at very different heights: a stair, a slope, a shaft...), and its single
 * {@link Gate#representativeWorld representative} can be far from where the route really crosses the
 * border. Walking to the representatives sent the entity to cells it could not reach (a cave twenty
 * blocks below, a cell inside a wall...). Here, each border crossing is resolved to a pair of
 * <em>adjacent</em> cells (one in each gate, one block apart across the border) chosen closest to where
 * the walk currently is, and these two cells are the waypoints.
 */
final class RouteWaypoints {

    private final List<int[]> cells = new ArrayList<>();
    private final List<Gate> owners = new ArrayList<>();

    int size() { return cells.size(); }

    int[] cell(int index) { return cells.get(index); }

    /** The gate whose cluster contains the waypoint. */
    Gate owner(int index) { return owners.get(index); }

    private void add(int[] cell, Gate owner) {
        cells.add(cell);
        owners.add(owner);
    }

    /**
     * @param route    ordered gates of the coarse route (never empty for a route that crosses a chunk border)
     * @param fromX    where the walk starts (block coordinates)
     */
    static RouteWaypoints build(List<Gate> route, int fromX, int fromY, int fromZ) {
        RouteWaypoints result = new RouteWaypoints();
        int[] ref = { fromX, fromY, fromZ };
        int[] entry = null; // cell of the current gate that the previous crossing arrives on
        for (int i = 0; i < route.size(); i++) {
            Gate gate = route.get(i);
            Gate next = i + 1 < route.size() ? route.get(i + 1) : null;
            boolean crossesOut = next != null && (next.chunkX != gate.chunkX || next.chunkZ != gate.chunkZ);

            boolean hadEntry = entry != null;
            if (hadEntry) {
                result.add(entry, gate);
                ref = entry;
                entry = null;
            }
            if (crossesOut) {
                int[][] pair = crossing(gate, next, ref);
                if (pair == null) { // stale data, no adjacent cells: the representatives are the best guess
                    pair = new int[][] { gate.representativeWorld, next.representativeWorld };
                }
                if (!hadEntry || !sameCell(pair[0], ref)) {
                    result.add(pair[0], gate);
                }
                ref = pair[0];
                entry = pair[1];
            } else if (!hadEntry) {
                // a gate crossed neither way (first or last of the route, same chunk around it)
                int[] member = nearestMember(gate, ref);
                result.add(member, gate);
                ref = member;
            }
        }
        return result;
    }

    /** {exit cell in {@code from}, entry cell in {@code to}}, adjacent across the border, closest to {@code ref}. */
    private static int[][] crossing(Gate from, Gate to, int[] ref) {
        Direction edge = from.edge;
        Map<Long, List<int[]>> byColumn = new HashMap<>();
        for (int[] member : to.memberBlocksWorld) {
            byColumn.computeIfAbsent(column(member[0], member[2]), k -> new ArrayList<>()).add(member);
        }
        int[][] best = null;
        double bestScore = Double.MAX_VALUE;
        for (int[] exit : from.memberBlocksWorld) {
            List<int[]> across = byColumn.get(column(exit[0] + edge.dx, exit[2] + edge.dz));
            if (across == null) continue;
            for (int[] entry : across) {
                if (Math.abs(entry[1] - exit[1]) > 1) continue;
                double score = distanceSquared(exit, ref) + 0.01 * distanceSquared(entry, ref);
                if (score < bestScore) {
                    bestScore = score;
                    best = new int[][] { exit, entry };
                }
            }
        }
        return best;
    }

    private static int[] nearestMember(Gate gate, int[] ref) {
        int[] best = gate.representativeWorld;
        double bestDistance = Double.MAX_VALUE;
        for (int[] member : gate.memberBlocksWorld) {
            double d = distanceSquared(member, ref);
            if (d < bestDistance) {
                bestDistance = d;
                best = member;
            }
        }
        return best;
    }

    private static long column(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static boolean sameCell(int[] a, int[] b) {
        return a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
    }

    private static double distanceSquared(int[] a, int[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return dx * dx + dy * dy + dz * dz;
    }
}
