package skwhy.chunkpath.gate;

import skwhy.chunkpath.Direction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Résultat complet de l'analyse chunk-à-chunk pour un chunk donné. */
public final class ChunkGateData {

    public final int chunkX, chunkZ;
    private List<Gate> gates;
    private final Map<Direction, List<Gate>> byEdge = new EnumMap<>(Direction.class);

    public ChunkGateData(int chunkX, int chunkZ, List<Gate> gates) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.gates = new ArrayList<>(gates);
        reindex();
    }

    private void reindex() {
        for (Direction d : Direction.values()) byEdge.put(d, new ArrayList<>());
        for (Gate g : gates) byEdge.get(g.edge).add(g);
    }

    public List<Gate> gates() {
        return gates;
    }

    public List<Gate> onEdge(Direction dir) {
        return byEdge.get(dir);
    }

    public Gate byId(int id) {
        for (Gate g : gates) if (g.id == id) return g;
        return null;
    }

    /** Retire définitivement les portes jugées inutiles (culs-de-sac confirmés). */
    public void prune(Set<Integer> gateIdsToRemove) {
        if (gateIdsToRemove.isEmpty()) return;
        gates.removeIf(g -> gateIdsToRemove.contains(g.id));
        reindex();
    }
}
