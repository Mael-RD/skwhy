package skwhy.chunkpath;

import skwhy.chunkpath.block.PathCostRules;
import skwhy.chunkpath.gate.ChunkGateData;
import skwhy.chunkpath.gate.Gate;
import skwhy.chunkpath.gate.GateExtractor;
import skwhy.chunkpath.graph.ChunkNavMesh;
import skwhy.chunkpath.nbt.NbtTag;
import skwhy.chunkpath.region.RegionChunkLoader;

import java.nio.file.Path;

/**
 * Point d'entrée : analyse UN chunk (obligatoirement déjà généré sur disque, sinon
 * ChunkNotGeneratedException) et produit ses portes chunk-à-chunk.
 *
 * Usage :
 * <pre>
 *   ChunkPathAnalyzer analyzer = new ChunkPathAnalyzer(worldRegionFolder, PathCostRules.defaults());
 *   ChunkGateData data = analyzer.analyze(chunkX, chunkZ);
 * </pre>
 *
 * Thread-safety : sans état partagé mutable entre chunks, une instance peut être
 * réutilisée (et appelée en parallèle) pour analyser plusieurs chunks, tant que
 * `regionFolder` désigne bien le même monde. À lancer hors du thread principal du
 * serveur (aucun appel Bukkit nécessitant le main thread n'est fait ici).
 */
public final class ChunkPathAnalyzer {

    private final Path regionFolder;
    private final PathCostRules costRules;
    private final int gateMergeThreshold;

    public ChunkPathAnalyzer(Path regionFolder, PathCostRules costRules) {
        this(regionFolder, costRules, GateExtractor.DEFAULT_MERGE_THRESHOLD);
    }

    public ChunkPathAnalyzer(Path regionFolder, PathCostRules costRules, int gateMergeThreshold) {
        this.regionFolder = regionFolder;
        this.costRules = costRules;
        this.gateMergeThreshold = gateMergeThreshold;
    }

    /**
     * @throws skwhy.chunkpath.region.ChunkNotGeneratedException si le chunk
     *         n'existe pas encore sur disque (ce système ne génère jamais de chunk lui-même)
     */
    public ChunkGateData analyze(int chunkX, int chunkZ) {
        ChunkNavMesh mesh = buildMesh(chunkX, chunkZ);
        java.util.List<Gate> gates = GateExtractor.extract(mesh, chunkX, chunkZ, gateMergeThreshold);
        return new ChunkGateData(chunkX, chunkZ, gates);
    }

    /**
     * Construit uniquement le graphe de praticabilité d'un chunk (mêmes règles de coût,
     * même lecture région que {@link #analyze}), sans en extraire les portes. Utilisé par
     * {@code skwhy.chunkpath.pathfinding} pour situer un point arbitraire (pas forcément
     * une porte) par rapport aux portes déjà connues d'un chunk, en réutilisant le même
     * système de praticabilité/pathfinding interne ({@link ChunkNavMesh#dijkstraMultiSource})
     * plutôt que d'en dupliquer un second.
     *
     * @throws skwhy.chunkpath.region.ChunkNotGeneratedException si le chunk
     *         n'existe pas encore sur disque
     */
    public ChunkNavMesh buildMesh(int chunkX, int chunkZ) {
        NbtTag root = RegionChunkLoader.loadChunkRoot(regionFolder, chunkX, chunkZ);
        return new ChunkNavMesh(root, costRules);
    }

    /** Les règles de coût utilisées par cette instance (mêmes règles pour toute analyse ou pathfinding sur ce monde). */
    public PathCostRules costRules() {
        return costRules;
    }

    /** Même dossier région et seuil de fusion, mais avec des règles de coût différentes (analyse ponctuelle). */
    public ChunkPathAnalyzer withCostRules(PathCostRules otherCostRules) {
        return new ChunkPathAnalyzer(regionFolder, otherCostRules, gateMergeThreshold);
    }
}
