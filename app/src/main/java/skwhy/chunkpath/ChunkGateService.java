package skwhy.chunkpath;

import skwhy.chunkpath.block.PathCostRules;
import skwhy.chunkpath.gate.ChunkGateData;
import skwhy.chunkpath.gate.ChunkGateLinker;
import skwhy.chunkpath.gate.ChunkGateStore;

import java.util.List;
import java.util.Map;
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

    /** L'analyseur (donc les règles de coût et le dossier région) utilisé par cette instance. */
    public ChunkPathAnalyzer analyzer() {
        return analyzer;
    }

    /**
     * Renvoie les données du chunk. Si déjà connu (mémoire ou disque), le relit tel
     * quel. Sinon, l'analyse depuis le fichier région, le lie à tout voisin déjà connu
     * (mémoire ou disque), sauvegarde le résultat (et tout voisin modifié par la
     * liaison/élagage), puis le renvoie.
     *
     * @throws skwhy.chunkpath.region.ChunkNotGeneratedException si le chunk
     *         n'est pas généré sur disque
     */
    public synchronized ChunkGateData getOrAnalyze(int chunkX, int chunkZ) {
        long key = pack(chunkX, chunkZ);
        ChunkGateData existing = cache.get(key);
        if (existing != null) return existing;

        ChunkGateData fromDisk = store.load(worldName, chunkX, chunkZ);
        if (fromDisk != null) {
            cache.put(key, fromDisk);
            return fromDisk;
        }

        return reanalyze(chunkX, chunkZ);
    }

    /**
     * Force une nouvelle analyse du chunk depuis le fichier région, qu'il ait déjà été
     * enregistré (mémoire ou disque) ou non, le lie aux voisins déjà connus, persiste le
     * résultat (et tout voisin modifié par la liaison/élagage), et remplace la version en
     * cache/sur disque le cas échéant. Utilise les règles de coût par défaut de cette instance.
     *
     * @throws skwhy.chunkpath.region.ChunkNotGeneratedException si le chunk
     *         n'est pas généré sur disque
     */
    public synchronized ChunkGateData reanalyze(int chunkX, int chunkZ) {
        return reanalyze(chunkX, chunkZ, null);
    }

    /**
     * Comme {@link #reanalyze(int, int)}, mais avec des règles de coût ponctuelles pour cette
     * seule analyse (ex: appelées explicitement depuis un script), au lieu des règles par défaut
     * de cette instance. {@code customCostRules} peut être {@code null} pour revenir aux règles
     * par défaut. Le reste du pipeline (liaison, persistance) est strictement identique à
     * {@link #reanalyze(int, int)} : un seul système, seules les règles de coût changent.
     *
     * @throws skwhy.chunkpath.region.ChunkNotGeneratedException si le chunk
     *         n'est pas généré sur disque
     */
    public synchronized ChunkGateData reanalyze(int chunkX, int chunkZ, PathCostRules customCostRules) {
        ChunkPathAnalyzer effectiveAnalyzer = customCostRules != null ? analyzer.withCostRules(customCostRules) : analyzer;
        long key = pack(chunkX, chunkZ);
        ChunkGateData data = effectiveAnalyzer.analyze(chunkX, chunkZ);
        cache.put(key, data);

        for (Direction dir : Direction.values()) {
            ChunkGateData neighbor = getIfKnown(chunkX + dir.dx, chunkZ + dir.dz);
            if (neighbor != null) {
                ChunkGateLinker.link(data, neighbor, dir);
                // le voisin peut avoir perdu des portes (élagage) : on ré-enregistre son état à jour
                store.save(worldName, neighbor);
            }
        }
        store.save(worldName, data);
        return data;
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