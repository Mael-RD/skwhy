package skwhy.chunkpath.debug;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings of the long pathfinding that can be changed at runtime ({@code /cpdebug set <key> <value>}, e.g. through
 * RCON) to compare behaviours in one debug session without rebuilding or restarting the server.
 */
public final class ChunkPathTuning {

    /** 0 = waypoint reached within a block, 1 = also reached when the next chunk is entered, 2 = same but the exit cell is skipped. */
    public static volatile int switchMode = 1;
    /** Weight of the distance to the goal when picking the cell where a route crosses a chunk border (0 = nearest to the mob only). */
    public static volatile double goalWeight = 0.5;
    /** Weight of the distance to the straight line start -> goal when picking the crossing cell. */
    public static volatile double lineWeight = 1.0;
    /** Greedy factor of the A* heuristic. */
    public static volatile float fudging = 1.5F;
    /** A* visited nodes limit = path length x this. */
    public static volatile int nodesFactor = 16;
    /** Shortcut: before a leg, try to walk straight to a waypoint up to this many waypoints ahead (0 = off). */
    public static volatile int lookahead = 8;
    /** A shortcut is accepted when its length is at most this multiple of the gate-by-gate polyline. */
    public static volatile double lookaheadRatio = 1.3;
    /** Longest straight distance (blocks) tried by a shortcut. */
    public static volatile double lookaheadRange = 40.0;
    /** Maximum path length (blocks) of one A* leg. */
    public static volatile float legLength = 48F;
    /** Default size (blocks) under which a patch more expensive than its surroundings is ignored (0 = custom costs at full precision). */
    public static volatile int localScale = 4;
    /** Cost added per step next to a worse cell (wall, drop, expensive floor): keeps the entity in the middle of a path. 0 = off. */
    public static volatile double edgeWeight = 0.8;
    /** Distance (blocks) over which the centring cost fades out. */
    public static volatile int edgeDistance = 2;
    /** Straighten the A* legs (string pulling), only where it costs no more than the A* path. */
    public static volatile boolean smooth = true;
    /** A shortcut never skips more than this many A* nodes at once. */
    public static volatile int smoothSpan = 24;
    /** Weight of the floor cost of the two cells of a border crossing when the leg to it is planned (0 = distance only). */
    public static volatile double crossCost = 1.0;
    /** Cost added for each block of margin missing (0..3) on the weaker side of a crossing cell: crossings go through the middle of a path. */
    public static volatile double crossMargin = 4.0;
    /** Records the position of every long navigation in plugins/SkWhy/traces. */
    public static volatile boolean trace = false;

    private ChunkPathTuning() {}

    public static Map<String, String> describe() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("switch", String.valueOf(switchMode));
        map.put("goalweight", String.valueOf(goalWeight));
        map.put("lineweight", String.valueOf(lineWeight));
        map.put("fudging", String.valueOf(fudging));
        map.put("nodes", String.valueOf(nodesFactor));
        map.put("lookahead", String.valueOf(lookahead));
        map.put("lookaheadratio", String.valueOf(lookaheadRatio));
        map.put("lookaheadrange", String.valueOf(lookaheadRange));
        map.put("leglength", String.valueOf(legLength));
        map.put("localscale", String.valueOf(localScale));
        map.put("edgeweight", String.valueOf(edgeWeight));
        map.put("edgedist", String.valueOf(edgeDistance));
        map.put("smooth", String.valueOf(smooth));
        map.put("smoothspan", String.valueOf(smoothSpan));
        map.put("crosscost", String.valueOf(crossCost));
        map.put("crossmargin", String.valueOf(crossMargin));
        map.put("trace", String.valueOf(trace));
        return map;
    }

    /** @return an error message, or null when the setting was applied */
    public static String set(String key, String value) {
        try {
            switch (key) {
                case "switch" -> switchMode = Integer.parseInt(value);
                case "goalweight" -> goalWeight = Double.parseDouble(value);
                case "lineweight" -> lineWeight = Double.parseDouble(value);
                case "fudging" -> fudging = Float.parseFloat(value);
                case "nodes" -> nodesFactor = Integer.parseInt(value);
                case "lookahead" -> lookahead = Integer.parseInt(value);
                case "lookaheadratio" -> lookaheadRatio = Double.parseDouble(value);
                case "lookaheadrange" -> lookaheadRange = Double.parseDouble(value);
                case "leglength" -> legLength = Float.parseFloat(value);
                case "localscale" -> localScale = Integer.parseInt(value);
                case "edgeweight" -> edgeWeight = Double.parseDouble(value);
                case "edgedist" -> edgeDistance = Integer.parseInt(value);
                case "smooth" -> smooth = value.equals("on") || value.equals("true");
                case "smoothspan" -> smoothSpan = Integer.parseInt(value);
                case "crosscost" -> crossCost = Double.parseDouble(value);
                case "crossmargin" -> crossMargin = Double.parseDouble(value);
                case "trace" -> trace = value.equals("on") || value.equals("true");
                default -> { return "clé inconnue : " + key + " (" + String.join(", ", describe().keySet()) + ")"; }
            }
        } catch (NumberFormatException e) {
            return "valeur invalide : " + value;
        }
        return null;
    }
}
