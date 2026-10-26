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
    /** index of an exit cell -> {gate it leaves, gate it enters}: the crossing can be chosen again once the terrain is loaded. */
    private final Map<Integer, Gate[]> crossings = new HashMap<>();
    private final java.util.Set<Integer> refined = new java.util.HashSet<>();

    /** Floor cost of a cell: negative = blocked / wall, {@link Integer#MIN_VALUE} = unknown (chunk not loaded, no floor). */
    interface CellCost {
        int of(int x, int y, int z);
    }

    int size() { return cells.size(); }

    int[] cell(int index) { return cells.get(index); }

    /** The gate whose cluster contains the waypoint. */
    Gate owner(int index) { return owners.get(index); }

    private void add(int[] cell, Gate owner) {
        cells.add(cell);
        owners.add(owner);
    }

    /** Without a goal: every crossing is simply the one closest to the walk. */
    static RouteWaypoints build(List<Gate> route, int fromX, int fromY, int fromZ) {
        return build(route, fromX, fromY, fromZ, null);
    }

    /**
     * @param route    ordered gates of the coarse route (never empty for a route that crosses a chunk border)
     * @param fromX    where the walk starts (block coordinates)
     * @param goal     the final destination (block coordinates), or null: crossings are slightly drawn toward it and toward the straight line to it
     */
    static RouteWaypoints build(List<Gate> route, int fromX, int fromY, int fromZ, int[] goal) {
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
                int[][] pair = crossing(gate, next, ref, new int[] { fromX, fromY, fromZ }, goal, null);
                if (pair == null) { // stale data, no adjacent cells: the representatives are the best guess
                    pair = new int[][] { gate.representativeWorld, next.representativeWorld };
                }
                if (!hadEntry || !sameCell(pair[0], ref)) {
                    result.add(pair[0], gate);
                    result.crossings.put(result.size() - 1, new Gate[] { gate, next });
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

    /**
     * Chooses the crossing between the gates again, from where the walk really is and with the real floor costs of the
     * cells (the first choice was made from the distances alone, long before the terrain around was loaded): a crossing
     * is moved onto a cheap path rather than onto the expensive cell that happens to be closest, and away from its
     * edges. Done at most once per waypoint. The cells of the waypoint and of the next one (entry) are replaced.
     *
     * @return true when the waypoints changed
     */
    boolean refine(int index, int[] here, int[] goal, CellCost cost) {
        Gate[] gates = crossings.get(index);
        if (gates == null || index + 1 >= cells.size() || !refined.add(index)) return false;
        int[][] pair = crossing(gates[0], gates[1], here, here, goal, cost);
        if (pair == null || (sameCell(pair[0], cells.get(index)) && sameCell(pair[1], cells.get(index + 1)))) return false;
        cells.set(index, pair[0]);
        cells.set(index + 1, pair[1]);
        return true;
    }

    /** {exit cell in {@code from}, entry cell in {@code to}}, adjacent across the border, closest to {@code ref}. */
    private static int[][] crossing(Gate from, Gate to, int[] ref, int[] start, int[] goal, CellCost cost) {
        double goalWeight = goal == null ? 0.0 : skwhy.chunkpath.debug.ChunkPathTuning.goalWeight;
        double lineWeight = goal == null ? 0.0 : skwhy.chunkpath.debug.ChunkPathTuning.lineWeight;
        Direction edge = from.edge;
        Map<Long, List<int[]>> byColumn = new HashMap<>();
        for (int[] member : to.memberBlocksWorld) {
            byColumn.computeIfAbsent(column(member[0], member[2]), k -> new ArrayList<>()).add(member);
        }
        Map<Long, int[]> fromByColumn = null;
        double costWeight = skwhy.chunkpath.debug.ChunkPathTuning.crossCost;
        double marginWeight = skwhy.chunkpath.debug.ChunkPathTuning.crossMargin;
        if (cost != null && marginWeight > 0.0) {
            fromByColumn = new HashMap<>();
            for (int[] member : from.memberBlocksWorld) fromByColumn.put(column(member[0], member[2]), member);
        }
        int[][] best = null;
        double bestScore = Double.MAX_VALUE;
        for (int[] exit : from.memberBlocksWorld) {
            List<int[]> across = byColumn.get(column(exit[0] + edge.dx, exit[2] + edge.dz));
            if (across == null) continue;
            for (int[] entry : across) {
                if (Math.abs(entry[1] - exit[1]) > 1) continue;
                double score = Math.sqrt(distanceSquared(exit, ref)) + 0.1 * Math.sqrt(distanceSquared(entry, ref));
                if (cost != null) {
                    int exitCost = cost.of(exit[0], exit[1], exit[2]);
                    int entryCost = cost.of(entry[0], entry[1], entry[2]);
                    if (exitCost == Integer.MIN_VALUE || entryCost == Integer.MIN_VALUE) continue; // unknown: keep the earlier choice
                    score += costWeight * (Math.max(exitCost, 0) + Math.max(entryCost, 0));
                    if (exitCost < 0 || entryCost < 0) score += 1.0E6;
                    if (fromByColumn != null && exitCost >= 0) {
                        int margin = margin(exit, edge, fromByColumn, exitCost, cost);
                        score += marginWeight * (3 - margin);
                    }
                }
                if (goalWeight != 0.0) score += goalWeight * Math.sqrt(distanceSquared(entry, goal));
                if (lineWeight != 0.0) score += lineWeight * distanceToLine(entry, start, goal);
                if (score < bestScore) {
                    bestScore = score;
                    best = new int[][] { exit, entry };
                }
            }
        }
        return best;
    }

    /** Blocks (0..3) of the border, on the weaker side of {@code exit}, that are as cheap as it is: how far it is from the edges of the path. */
    private static int margin(int[] exit, Direction edge, Map<Long, int[]> members, int exitCost, CellCost cost) {
        int lateralX = edge.dx != 0 ? 0 : 1, lateralZ = edge.dz != 0 ? 0 : 1;
        int weaker = 3;
        for (int side = -1; side <= 1; side += 2) {
            int free = 0;
            for (int step = 1; step <= 3; step++) {
                int x = exit[0] + side * step * lateralX, z = exit[2] + side * step * lateralZ;
                int[] member = members.get(column(x, z));
                if (member == null || Math.abs(member[1] - exit[1]) > 1) break;
                int c = cost.of(member[0], member[1], member[2]);
                if (c < 0 || c > 2 * exitCost + 1) break;
                free++;
            }
            weaker = Math.min(weaker, free);
        }
        return weaker;
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

    /** Horizontal distance from {@code p} to the straight line {@code a} -> {@code b}. */
    private static double distanceToLine(int[] p, int[] a, int[] b) {
        double abx = b[0] - a[0], abz = b[2] - a[2];
        double length = Math.sqrt(abx * abx + abz * abz);
        if (length < 1.0E-6) return Math.sqrt((p[0] - a[0]) * (double) (p[0] - a[0]) + (p[2] - a[2]) * (double) (p[2] - a[2]));
        return Math.abs(abx * (p[2] - a[2]) - abz * (p[0] - a[0])) / length;
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
