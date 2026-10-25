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
 * Il n'existe aucune règle de coût par défaut : les {@link PathCostRules} sont toujours fournies
 * par l'appelant à chaque analyse.
 *
 * Usage :
 * <pre>
 *   ChunkPathAnalyzer analyzer = new ChunkPathAnalyzer(worldRegionFolder);
 *   ChunkGateData data = analyzer.analyze(chunkX, chunkZ, costRules);
 * </pre>
 *
 * Thread-safety : sans état partagé mutable entre chunks, une instance peut être
 * réutilisée (et appelée en parallèle) pour analyser plusieurs chunks, tant que
 * `regionFolder` désigne bien le même monde. À lancer hors du thread principal du
 * serveur (aucun appel Bukkit nécessitant le main thread n'est fait ici).
 */
public final class ChunkPathAnalyzer {

    private final Path regionFolder;

    public ChunkPathAnalyzer(Path regionFolder) {
        this.regionFolder = regionFolder;
    }

    /**
     * @throws skwhy.chunkpath.region.ChunkNotGeneratedException si le chunk
     *         n'existe pas encore sur disque (ce système ne génère jamais de chunk lui-même)
     */
    public ChunkGateData analyze(int chunkX, int chunkZ, PathCostRules costRules) {
        ChunkNavMesh mesh = buildMesh(chunkX, chunkZ, costRules);
        java.util.List<Gate> gates = GateExtractor.extract(mesh, chunkX, chunkZ);
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
    public ChunkNavMesh buildMesh(int chunkX, int chunkZ, PathCostRules costRules) {
        NbtTag root = RegionChunkLoader.loadChunkRoot(regionFolder, chunkX, chunkZ);
        return new ChunkNavMesh(root, costRules);
    }
}
