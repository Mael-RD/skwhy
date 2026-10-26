package skwhy.chunkpath;

import skwhy.chunkpath.block.PathCostRules;
import skwhy.chunkpath.gate.ChunkGateData;
import skwhy.chunkpath.gate.ChunkGateLinker;
import skwhy.chunkpath.gate.Gate;
import skwhy.chunkpath.gate.ChunkGateStore;

import skwhy.chunkpath.graph.ChunkNavMesh;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Garde en cache les chunks déjà analysés et, à chaque nouvelle analyse, lie
 * automatiquement le chunk aux voisins déjà connus (dans les 4 directions) — qu'ils
 * soient déjà en mémoire OU déjà présents sur disque (ex: analysés lors d'un précédent
 * démarrage du serveur). C'est ce composant qui doit être appelé par le reste du
 * système, pas ChunkPathAnalyzer directement, pour que la liaison + l'élagage des
 * culs-de-sac se fassent au bon moment et soient persistés.
 *
 * Persistance : chaque chunk analysé est écrit via {@link ChunkGateStore} dans
 * {@code <dossier du plugin>/chunkpath-data/<nom du monde>/}. Si un chunk existe déjà
 * sur disque, il est relu directement (jamais ré-analysé), et sert de référence pour
 * savoir si un voisin donné a déjà été traité — c'est cette lecture disque qui répond
 * à "le chunk adjacent a-t-il déjà été calculé ?", indépendamment de ce qui est en
 * mémoire à l'instant présent.
 *
 * Une instance est scopée à UN SEUL monde (le nom du monde est fixé au constructeur).
 * Crée une instance par monde si tu en gères plusieurs.
 *
 * Thread-safety : {@link #getOrAnalyze} est synchronisé sur l'instance. Si tu analyses
 * beaucoup de chunks en parallèle sur plusieurs threads (recommandé, cf. ChunkPathAnalyzer),
 * utilise UNE SEULE instance de ChunkGateService partagée par monde plutôt que d'en créer
 * une par thread, sinon deux threads pourraient écrire le même fichier voisin en même
 * temps et corrompre le résultat (l'écriture elle-même reste atomique grâce à
 * ChunkGateStore, mais un décompte de portes voisines calculé en même temps sur deux
 * threads pourrait être incohérent sans cette synchronisation).
 */
public final class ChunkGateService {

    private final ChunkPathAnalyzer analyzer;
    private final ChunkGateStore store;
    private final String worldName;
    private final Map<Long, ChunkGateData> cache = new ConcurrentHashMap<>();

    public ChunkGateService(ChunkPathAnalyzer analyzer, ChunkGateStore store, String worldName) {
        this.analyzer = analyzer;
        this.store = store;
        this.worldName = worldName;
    }

    /** L'analyseur (donc le dossier région) utilisé par cette instance. */
    public ChunkPathAnalyzer analyzer() {
        return analyzer;
    }

    /**
     * Renvoie les données du chunk. Si déjà connu (mémoire ou disque), le relit tel
     * quel. Sinon, l'analyse depuis le fichier région avec {@code costRules}, le lie à tout
     * voisin déjà connu (mémoire ou disque), sauvegarde le résultat (et tout voisin modifié par la
     * liaison/élagage), puis le renvoie. Il n'y a pas de règles par défaut : un chunk inconnu
     * ne peut pas être analysé sans règles.
     *
     * @throws skwhy.chunkpath.region.ChunkNotGeneratedException si le chunk
     *         n'est pas généré sur disque
     */
    public synchronized ChunkGateData getOrAnalyze(int chunkX, int chunkZ, PathCostRules costRules) {
        long key = pack(chunkX, chunkZ);
        ChunkGateData existing = cache.get(key);
        if (existing != null) return existing;

        ChunkGateData fromDisk = store.load(worldName, chunkX, chunkZ);
        if (fromDisk != null) {
            cache.put(key, fromDisk);
            return fromDisk;
        }

        return reanalyze(chunkX, chunkZ, costRules);
    }

    /**
     * Force une nouvelle analyse du chunk depuis le fichier région avec {@code costRules}, qu'il
     * ait déjà été enregistré (mémoire ou disque) ou non, le lie aux voisins déjà connus, persiste
     * le résultat (et tout voisin modifié par la liaison/élagage), et remplace la version en
     * cache/sur disque le cas échéant.
     *
     * @throws IllegalArgumentException si {@code costRules} est {@code null} (aucune règle par défaut)
     * @throws skwhy.chunkpath.region.ChunkNotGeneratedException si le chunk
     *         n'est pas généré sur disque
     */
    public synchronized ChunkGateData reanalyze(int chunkX, int chunkZ, PathCostRules costRules) {
        if (costRules == null) throw new IllegalArgumentException("aucune règle de coût fournie");
        long key = pack(chunkX, chunkZ);
        ChunkGateData data = analyzer.analyze(chunkX, chunkZ, costRules);
        cache.put(key, data);

        String replacedPrefix = chunkX + "," + chunkZ + ",";
        for (Direction dir : Direction.values()) {
            ChunkGateData neighbor = getIfKnown(chunkX + dir.dx, chunkZ + dir.dz);
            if (neighbor != null) {
                // the gate ids of the previous analysis of this chunk are meaningless now (they are renumbered):
                // links kept by the neighbor would silently point to other gates and create impossible routes
                for (Gate gate : neighbor.gates()) {
                    gate.neighborLinks.removeIf(id -> id.startsWith(replacedPrefix));
                    gate.neighborCosts.keySet().removeIf(id -> id.startsWith(replacedPrefix));
                }
                ChunkGateLinker.link(data, neighbor, dir);
                computeCrossingCosts(data, neighbor, dir, costRules);
                // le voisin peut avoir perdu des portes (élagage) : on ré-enregistre son état à jour
                store.save(worldName, neighbor);
            }
        }
        store.save(worldName, data);
        return data;
    }

    /**
     * Pour chaque paire de portes liées entre {@code a} et {@code b}, calcule le coût du chemin au sol
     * du bloc central de l'une au bloc central de l'autre : on passe par la paire de cellules adjacentes de
     * part et d'autre de la frontière la moins chère (coût dans le chunk de a jusqu'à la cellule, puis
     * dans celui de b depuis la cellule, comptes avec le coût d'entrée de chaque noeud, comme le Dijkstra
     * du maillage). Le même coût est enregistré des deux côtés. Si le calcul est impossible (chunk
     * illisible, centre non praticable, aucun chemin), la paire garde l'estimation par la distance.
     */
    private void computeCrossingCosts(ChunkGateData a, ChunkGateData b, Direction dirAtoB, PathCostRules costRules) {
        ChunkNavMesh meshA;
        ChunkNavMesh meshB;
        try {
            meshA = analyzer.buildMesh(a.chunkX, a.chunkZ, costRules);
            meshB = analyzer.buildMesh(b.chunkX, b.chunkZ, costRules);
        } catch (RuntimeException e) {
            return;
        }
        Map<String, int[]> distCache = new HashMap<>();
        String prefixB = b.chunkX + "," + b.chunkZ + ",";
        for (Gate ga : a.gates()) {
            for (String link : ga.neighborLinks) {
                if (!link.startsWith(prefixB)) continue;
                Gate gb = b.byId(Integer.parseInt(link.substring(prefixB.length())));
                if (gb == null) continue;
                int cost = crossingCost(ga, gb, meshA, meshB, dirAtoB, distCache);
                if (cost >= 0) {
                    ga.neighborCosts.put(gb.globalId(), cost);
                    gb.neighborCosts.put(ga.globalId(), cost);
                }
            }
        }
    }

    /** @return le coût, ou -1 si aucun chemin ne relie les deux blocs centraux. */
    private static int crossingCost(Gate ga, Gate gb, ChunkNavMesh meshA, ChunkNavMesh meshB,
                                    Direction dirAtoB, Map<String, int[]> distCache) {
        int repA = nodeOf(meshA, ga, ga.representativeWorld);
        int repB = nodeOf(meshB, gb, gb.representativeWorld);
        if (repA < 0 || repB < 0) return -1;
        int[] distA = distCache.computeIfAbsent(ga.globalId(), k -> meshA.dijkstraFrom(repA, Integer.MAX_VALUE));
        int[] distB = distCache.computeIfAbsent(gb.globalId(), k -> meshB.dijkstraFrom(repB, Integer.MAX_VALUE));

        Set<Long> membersB = new HashSet<>();
        for (int[] m : gb.memberBlocksWorld) membersB.add(blockKey(m[0], m[1], m[2]));

        long best = Long.MAX_VALUE;
        for (int[] m : ga.memberBlocksWorld) {
            if (!membersB.contains(blockKey(m[0] + dirAtoB.dx, m[1], m[2] + dirAtoB.dz))) continue;
            int nodeA = nodeOf(meshA, ga, m);
            int nodeB = nodeOf(meshB, gb, new int[]{m[0] + dirAtoB.dx, m[1], m[2] + dirAtoB.dz});
            if (nodeA < 0 || nodeB < 0) continue;
            if (distA[nodeA] == Integer.MAX_VALUE || distB[nodeB] == Integer.MAX_VALUE) continue;
            // a -> b -> centre de gb, avec le coût d'entrée de chaque noeud : distB est mesuré depuis le centre,
            // le trajet inverse (b -> centre) coûte distB[b] - cost(b) + cost(centre), et franchir la frontière coûte cost(b)
            long total = (long) distA[nodeA] + distB[nodeB] + meshB.cost(repB);
            best = Math.min(best, total);
        }
        return best == Long.MAX_VALUE ? -1 : (int) Math.min(best, Integer.MAX_VALUE);
    }

    private static int nodeOf(ChunkNavMesh mesh, Gate gate, int[] world) {
        return mesh.nodeAt(world[0] - gate.chunkX * 16, world[1], world[2] - gate.chunkZ * 16);
    }

    private static long blockKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    /** Données d'un chunk si déjà connues (mémoire OU disque) — ne lance jamais d'analyse. */
    public ChunkGateData getIfKnown(int chunkX, int chunkZ) {
        long key = pack(chunkX, chunkZ);
        ChunkGateData cached = cache.get(key);
        if (cached != null) return cached;

        ChunkGateData loaded = store.load(worldName, chunkX, chunkZ);
        if (loaded != null) {
            cache.put(key, loaded);
        }
        return loaded;
    }

    /**
     * Toutes les données déjà connues (disque) pour ce monde, sans déclencher d'analyse. Le disque
     * est la source de vérité (chaque analyse est persistée immédiatement, cf. {@link #reanalyze}),
     * donc pas besoin de fusionner avec le cache mémoire ici.
     */
    public List<ChunkGateData> allKnown() {
        return store.all(worldName);
    }

    /** Lecture mémoire uniquement (ne consulte pas le disque). */
    public ChunkGateData peek(int chunkX, int chunkZ) {
        return cache.get(pack(chunkX, chunkZ));
    }

    /** Retire le chunk du cache mémoire (ne supprime pas le fichier sur disque). */
    public void invalidate(int chunkX, int chunkZ) {
        cache.remove(pack(chunkX, chunkZ));
    }

    /** Supprime définitivement l'enregistrement du chunk (mémoire ET disque). */
    public synchronized boolean delete(int chunkX, int chunkZ) {
        cache.remove(pack(chunkX, chunkZ));
        return store.delete(worldName, chunkX, chunkZ);
    }

    private static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }
}