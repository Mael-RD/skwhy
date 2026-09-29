package skwhy.chunkpath.pathfinding;

import skwhy.chunkpath.ChunkGateService;
import skwhy.chunkpath.ChunkPathAnalyzer;
import skwhy.chunkpath.gate.ChunkGateData;
import skwhy.chunkpath.gate.Gate;
import skwhy.chunkpath.graph.ChunkNavMesh;
import skwhy.chunkpath.region.ChunkNotGeneratedException;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.logging.Logger;

/**
 * Calcule le plus court chemin entre deux points d'un même monde, exprimé comme la
 * liste des portes ({@link Gate}) franchies, en réutilisant exclusivement les données
 * déjà produites par {@code skwhy.chunkpath.gate} (portes par chunk, distances internes
 * porte-à-porte) — jamais de ré-analyse de chunk pendant la recherche elle-même.
 *
 * Déroulement :
 * <ol>
 *   <li>Le chunk de départ et le chunk d'arrivée sont chacun résolus via
 *       {@link ChunkGateService#getOrAnalyze} (analyse complète si jamais traité, sinon
 *       relecture mémoire/disque déjà en cache — une seule fois chacun, jamais en boucle).</li>
 *   <li>Pour chacun des deux points, on situe son coût vers chaque porte déjà connue de
 *       son chunk par UN SEUL Dijkstra ({@link ChunkNavMesh#dijkstraMultiSource}) lancé
 *       depuis ce point sur le maillage de praticabilité du chunk — le même algorithme
 *       que celui utilisé pour calculer les distances porte-à-porte lors de l'extraction
 *       ({@code GateExtractor}), pas un second système. On ne relance PAS l'extraction de
 *       portes elle-même : les portes du chunk sont déjà connues.</li>
 *   <li>Un Dijkstra sur le graphe virtuel {@code départ -> portes -> ... -> portes -> arrivée}
 *       calcule le plus court chemin, en utilisant les distances internes déjà stockées sur
 *       chaque {@link Gate} pour les arcs intra-chunk, et un coût de franchissement constant
 *       pour les arcs inter-chunk ({@link Gate#neighborLinks}), puisqu'aucun coût de sol
 *       n'est stocké pour cette traversée (cf. {@link Gate}).</li>
 * </ol>
 *
 * Une porte menant vers un chunk jamais traité (pas de {@link ChunkGateData} connue en
 * mémoire ou sur disque) est ignorée comme un cul-de-sac : on ne déclenche jamais son
 * analyse depuis cette recherche (ce serait relire un fichier région en plein parcours,
 * potentiellement en boucle sur des chemins longs — exactement ce qu'il faut éviter pour
 * ne pas provoquer de lag).
 */
public final class GatePathfinder {

    private static final Logger LOGGER = Logger.getLogger(GatePathfinder.class.getName());

    private static final String START = "START";
    private static final String END = "END";

    private GatePathfinder() {
    }

    /** Comme {@link #findPath(ChunkGateService, int, int, int, int, int, int, int)}, avec le coût de franchissement de frontière par défaut ({@link skwhy.chunkpath.block.PathCostRules#defaultCost()} des règles de coût du monde). */
    public static List<Gate> findPath(ChunkGateService service,
                                       int startX, int startY, int startZ,
                                       int endX, int endY, int endZ) {
        return findPath(service, startX, startY, startZ, endX, endY, endZ,
                service.analyzer().costRules().defaultCost());
    }

    /**
     * @param borderCrossingCost coût constant appliqué à chaque franchissement d'une frontière
     *                           de chunk (arc {@link Gate#neighborLinks}), aucun coût de sol
     *                           réel n'étant connu pour cette traversée sans relire le chunk voisin
     * @return la liste ordonnée des portes franchies (départ -> arrivée), une liste vide si les
     *         deux points sont déjà mutuellement atteignables sans franchir aucune porte
     *         (impossible en pratique tant que départ/arrivée sont dans des chunks différents,
     *         mais possible si les deux chunks n'en ont aucune en commun), ou {@code null} si
     *         aucun chemin n'a pu être trouvé ou calculé (voir les logs pour la raison)
     */
    public static List<Gate> findPath(ChunkGateService service,
                                       int startX, int startY, int startZ,
                                       int endX, int endY, int endZ,
                                       int borderCrossingCost) {
        int startChunkX = Math.floorDiv(startX, 16);
        int startChunkZ = Math.floorDiv(startZ, 16);
        int endChunkX = Math.floorDiv(endX, 16);
        int endChunkZ = Math.floorDiv(endZ, 16);

        ChunkGateData startChunkData;
        ChunkGateData endChunkData;
        try {
            startChunkData = service.getOrAnalyze(startChunkX, startChunkZ);
            endChunkData = service.getOrAnalyze(endChunkX, endChunkZ);
        } catch (ChunkNotGeneratedException e) {
            LOGGER.warning("[ChunkPath] Calcul de chemin impossible : " + e.getMessage());
            return null;
        }

        ChunkPathAnalyzer analyzer = service.analyzer();

        Map<Integer, Integer> startGateCosts = costsFromPoint(
                analyzer, startChunkData, startChunkX, startChunkZ, startX, startY, startZ);
        if (startGateCosts == null) {
            LOGGER.warning("[ChunkPath] Position de départ (" + startX + "," + startY + "," + startZ
                    + ") non praticable, aucun chemin possible.");
            return null;
        }

        Map<Integer, Integer> endGateCosts = costsFromPoint(
                analyzer, endChunkData, endChunkX, endChunkZ, endX, endY, endZ);
        if (endGateCosts == null) {
            LOGGER.warning("[ChunkPath] Position d'arrivée (" + endX + "," + endY + "," + endZ
                    + ") non praticable, aucun chemin possible.");
            return null;
        }

        return searchGateGraph(service, startChunkData, endChunkData, startChunkX, startChunkZ,
                endChunkX, endChunkZ, startGateCosts, endGateCosts, borderCrossingCost);
    }

    // ── étape 1 : coût du point d'intérêt vers chaque porte déjà connue de son chunk ──

    /**
     * Un seul Dijkstra depuis le noeud le plus proche du point donné, sur le maillage de
     * praticabilité de son chunk, relevé jusqu'à chaque porte déjà connue de ce chunk.
     *
     * @return gateId -> coût depuis le point, ou {@code null} si le point n'est pas (ou pas
     *         près d'une position) praticable
     */
    private static Map<Integer, Integer> costsFromPoint(ChunkPathAnalyzer analyzer, ChunkGateData chunkData,
                                                          int chunkX, int chunkZ, int worldX, int worldY, int worldZ) {
        ChunkNavMesh mesh = analyzer.buildMesh(chunkX, chunkZ);

        int localX = worldX - chunkX * 16;
        int localZ = worldZ - chunkZ * 16;
        int startNode = nearestWalkableNode(mesh, localX, worldY, localZ);
        if (startNode < 0) return null;

        int[] dist = mesh.dijkstraMultiSource(new int[]{startNode}, Integer.MAX_VALUE);

        Map<Integer, Integer> costs = new HashMap<>();
        for (Gate gate : chunkData.gates()) {
            int min = Integer.MAX_VALUE;
            for (int[] block : gate.memberBlocksWorld) {
                int node = mesh.nodeAt(block[0] - chunkX * 16, block[1], block[2] - chunkZ * 16);
                if (node >= 0 && dist[node] < min) {
                    min = dist[node];
                }
            }
            if (min != Integer.MAX_VALUE) {
                costs.put(gate.id, min);
            }
        }
        return costs;
    }

    /** Cherche le noeud praticable exact à la position, sinon regarde 1-2 blocs au-dessus/en-dessous. */
    private static int nearestWalkableNode(ChunkNavMesh mesh, int x, int y, int z) {
        int direct = mesh.nodeAt(x, y, z);
        if (direct >= 0) return direct;
        for (int dy = 1; dy <= 2; dy++) {
            int up = mesh.nodeAt(x, y + dy, z);
            if (up >= 0) return up;
            int down = mesh.nodeAt(x, y - dy, z);
            if (down >= 0) return down;
        }
        return -1;
    }

    // ── étape 2 : Dijkstra sur le graphe virtuel départ -> portes -> ... -> portes -> arrivée ──

    private static List<Gate> searchGateGraph(ChunkGateService service,
                                               ChunkGateData startChunkData, ChunkGateData endChunkData,
                                               int startChunkX, int startChunkZ, int endChunkX, int endChunkZ,
                                               Map<Integer, Integer> startGateCosts, Map<Integer, Integer> endGateCosts,
                                               int borderCrossingCost) {
        Map<String, Integer> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        Map<String, Gate> gateByKey = new HashMap<>();
        Map<Long, ChunkGateData> chunkCache = new HashMap<>();
        chunkCache.put(packChunk(startChunkX, startChunkZ), startChunkData);
        chunkCache.put(packChunk(endChunkX, endChunkZ), endChunkData);
        for (Gate g : startChunkData.gates()) gateByKey.put(g.globalId(), g);
        for (Gate g : endChunkData.gates()) gateByKey.put(g.globalId(), g);

        PriorityQueue<Map.Entry<String, Integer>> pq = new PriorityQueue<>(Map.Entry.comparingByValue());

        dist.put(START, 0);
        for (Map.Entry<Integer, Integer> e : startGateCosts.entrySet()) {
            Gate g = startChunkData.byId(e.getKey());
            if (g != null) relax(dist, prev, pq, START, g.globalId(), e.getValue());
        }

        boolean reachedEnd = false;
        while (!pq.isEmpty()) {
            Map.Entry<String, Integer> top = pq.poll();
            String u = top.getKey();
            int d = top.getValue();
            if (d > dist.getOrDefault(u, Integer.MAX_VALUE)) continue; // entrée périmée
            if (END.equals(u)) {
                reachedEnd = true;
                break;
            }

            Gate gate = gateByKey.get(u);
            if (gate == null) continue; // START, ou clé inconnue (ne devrait pas arriver)

            if (gate.chunkX == endChunkX && gate.chunkZ == endChunkZ) {
                Integer toEnd = endGateCosts.get(gate.id);
                if (toEnd != null) relax(dist, prev, pq, u, END, toEnd);
            }

            for (Map.Entry<Integer, Integer> e : gate.internalDistances.entrySet()) {
                String otherKey = globalId(gate.chunkX, gate.chunkZ, e.getKey());
                Gate other = gateByKey.get(otherKey);
                if (other == null) {
                    ChunkGateData chunkData = chunkCache.get(packChunk(gate.chunkX, gate.chunkZ));
                    other = chunkData != null ? chunkData.byId(e.getKey()) : null;
                    if (other == null) continue;
                    gateByKey.put(otherKey, other);
                }
                relax(dist, prev, pq, u, otherKey, e.getValue());
            }

            for (String link : gate.neighborLinks) {
                Gate other = gateByKey.get(link);
                if (other == null) {
                    other = resolveNeighborGate(service, chunkCache, link);
                    if (other == null) continue; // chunk voisin jamais traité -> cul-de-sac ignoré
                    gateByKey.put(link, other);
                }
                relax(dist, prev, pq, u, link, borderCrossingCost);
            }
        }

        if (!reachedEnd) return null;

        List<Gate> path = new ArrayList<>();
        String cur = prev.get(END);
        while (cur != null && !cur.equals(START)) {
            Gate g = gateByKey.get(cur);
            if (g != null) path.add(g);
            cur = prev.get(cur);
        }
        Collections.reverse(path);
        return path;
    }

    /** Résout la porte d'un chunk voisin UNIQUEMENT si ce chunk est déjà connu (mémoire/disque déjà chargé) — ne lit jamais le fichier région. */
    private static Gate resolveNeighborGate(ChunkGateService service, Map<Long, ChunkGateData> chunkCache, String globalId) {
        String[] parts = globalId.split(",");
        if (parts.length < 3) return null;
        try {
            int cx = Integer.parseInt(parts[0]);
            int cz = Integer.parseInt(parts[1]);
            int id = Integer.parseInt(parts[2]);

            long key = packChunk(cx, cz);
            ChunkGateData data = chunkCache.get(key);
            if (data == null) {
                data = service.getIfKnown(cx, cz);
                if (data == null) return null;
                chunkCache.put(key, data);
            }
            return data.byId(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void relax(Map<String, Integer> dist, Map<String, String> prev,
                               PriorityQueue<Map.Entry<String, Integer>> pq, String from, String to, int weight) {
        int base = dist.getOrDefault(from, Integer.MAX_VALUE);
        if (base == Integer.MAX_VALUE) return;
        long candidate = (long) base + weight;
        if (candidate < dist.getOrDefault(to, Integer.MAX_VALUE)) {
            int newDist = (int) candidate;
            dist.put(to, newDist);
            prev.put(to, from);
            pq.add(new AbstractMap.SimpleEntry<>(to, newDist));
        }
    }

    private static String globalId(int chunkX, int chunkZ, int gateId) {
        return chunkX + "," + chunkZ + "," + gateId;
    }

    private static long packChunk(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }
}
