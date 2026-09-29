package skwhy.chunkpath.graph;

import skwhy.chunkpath.Direction;
import skwhy.chunkpath.block.BlockClassifier;
import skwhy.chunkpath.block.PathCostRules;
import skwhy.chunkpath.nbt.NbtTag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Construit, pour UN chunk, la grille de praticabilité 3D et le graphe de noeuds
 * praticables (4 directions horizontales + montée/descente d'1 bloc, plus les arcs de
 * grimpe verticale sur échelles/lianes, cf. {@link #buildAdjacency}).
 *
 * Optimisation clé : la grille est pré-remplie avec la valeur "air" (OCCUPIABLE) par
 * défaut. Une section dont la palette ne contient qu'une seule entrée (cas fréquent :
 * air/cave_air pur, mais aussi bedrock plein, etc.) n'a pas de tableau `data` à décoder :
 * si cette valeur unique correspond déjà à la valeur par défaut, on ne touche même pas
 * à la grille pour cette section (0 écriture, 0 décodage bit-à-bit). Sinon on remplit la
 * tranche en une passe simple sans dépaquetage de bits. Le dépaquetage bit-à-bit ne se
 * produit donc QUE pour les sections qui contiennent réellement plusieurs types de blocs.
 */
public final class ChunkNavMesh {

    private static final int GATE_UP_DOWN = 1; // on peut monter/descendre d'1 bloc

    private final int minY, maxY, heightBlocks;
    private final byte[] flags;   // occupiable/standable, dense
    private final String[] names; // nom de bloc, rempli uniquement là où utile (sol)

    // Graphe des noeuds praticables
    private final int[] nodeIndexGrid; // -1 si non praticable, sinon index dans nodeX/Y/Z
    private int[] nodeX, nodeY, nodeZ, nodeCost;
    private int nodeCount;
    private int[][] neighborIds;
    private int[][] neighborCosts;

    public ChunkNavMesh(NbtTag chunkRoot, PathCostRules costRules) {
        List<NbtTag> sections = chunkRoot.getList("sections");
        if (sections.isEmpty()) {
            throw new IllegalStateException("Chunk sans sections (pas de terrain)");
        }

        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (NbtTag s : sections) {
            int y = s.getByte("Y", (byte) 0);
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
        }
        this.minY = lo * 16;
        this.maxY = hi * 16 + 15;
        this.heightBlocks = maxY - minY + 1;

        int cellCount = 16 * 16 * heightBlocks;
        this.flags = new byte[cellCount];
        Arrays.fill(this.flags, BlockClassifier.OCCUPIABLE); // défaut = air
        this.names = new String[cellCount];

        for (NbtTag section : sections) {
            decodeSection(section);
        }

        this.nodeIndexGrid = new int[cellCount];
        Arrays.fill(this.nodeIndexGrid, -1);
        buildWalkableNodes(costRules);
        buildAdjacency();
    }

    // ---- décodage NBT ----

    private void decodeSection(NbtTag section) {
        int sectionY = section.getByte("Y", (byte) 0);
        int sectionBaseY = sectionY * 16;

        NbtTag blockStates = section.get("block_states");
        if (blockStates == null) {
            return; // pas de données de bloc -> reste "air" par défaut
        }

        List<NbtTag> palette = blockStates.getList("palette");
        if (palette.isEmpty()) {
            return;
        }

        // Pré-calcule flags/nom pour chaque entrée de la palette (petite, quelques
        // entrées en général) une seule fois, plutôt qu'à chaque bloc.
        byte[] paletteFlags = new byte[palette.size()];
        String[] paletteNames = new String[palette.size()];
        for (int i = 0; i < palette.size(); i++) {
            String name = palette.get(i).getString("Name", "minecraft:air");
            paletteNames[i] = name;
            paletteFlags[i] = BlockClassifier.classify(name);
        }

        boolean hasAnyAirLike = false;
        for (byte f : paletteFlags) {
            if (BlockClassifier.isOccupiable(f)) {
                hasAnyAirLike = true;
                break;
            }
        }

        // Fast-path : palette à une seule entrée (aucune donnée à dépaqueter), OU palette
        // ne contenant AUCUN bloc de type air/cave_air (section pleinement "solide" :
        // aucune case ne pourra jamais être praticable en tant que position, seul son
        // usage comme sol pour la couche du dessus compte, donc on peut se permettre
        // d'approximer toute la section par son bloc dominant plutôt que de dépaqueter
        // les 4096 entrées). Ce sont les deux cas visés par l'optimisation demandée.
        if (palette.size() == 1 || !hasAnyAirLike) {
            byte flag;
            String name;
            if (palette.size() == 1) {
                flag = paletteFlags[0];
                name = paletteNames[0];
            } else {
                // Choisit comme représentant le premier bloc "sol valide" de la palette ;
                // à défaut (que des murs/liquides sans aucun bloc plein classique, cas très
                // rare sans air), on retombe sur STANDABLE, l'hypothèse par défaut la plus sûre.
                int repIndex = -1;
                for (int i = 0; i < paletteFlags.length; i++) {
                    if (paletteFlags[i] == BlockClassifier.STANDABLE) {
                        repIndex = i;
                        break;
                    }
                }
                flag = BlockClassifier.STANDABLE;
                name = repIndex >= 0 ? paletteNames[repIndex] : paletteNames[0];
            }
            if (flag == BlockClassifier.OCCUPIABLE) {
                return; // identique au défaut "air" déjà en place : rien à faire
            }
            // Remplissage uniforme rapide, pas de dépaquetage de bits nécessaire.
            for (int ly = 0; ly < 16; ly++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int idx = cellIndex(lx, sectionBaseY + ly, lz);
                        flags[idx] = flag;
                        if (BlockClassifier.isStandable(flag) || BlockClassifier.isClimbable(flag)) {
                            names[idx] = name;
                        }
                    }
                }
            }
            return;
        }

        long[] data = blockStates.getLongArray("data");
        if (data == null) {
            return; // ne devrait pas arriver si palette.size() > 1, mais on reste défensif
        }

        int bitsPerEntry = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        long mask = (1L << bitsPerEntry) - 1;

        for (int i = 0; i < 4096; i++) {
            // Format post-1.16 (20w17a+) : les entrées sont packées de façon dense et
            // peuvent chevaucher deux longs consécutifs (pas de padding en fin de long,
            // contrairement au format pré-1.16). Il faut donc composer la valeur à partir
            // d'un éventuel second long dès que l'entrée déborde du premier.
            long bitIndex = (long) i * bitsPerEntry;
            int longIndex = (int) (bitIndex >>> 6);
            int bitOffset = (int) (bitIndex & 63);
            long value = data[longIndex] >>> bitOffset;
            if (bitOffset + bitsPerEntry > 64) {
                value |= data[longIndex + 1] << (64 - bitOffset);
            }
            int paletteIndex = (int) (value & mask);
            if (paletteIndex >= palette.size()) continue; // sécurité, ne devrait pas arriver

            byte flag = paletteFlags[paletteIndex];
            if (flag == BlockClassifier.OCCUPIABLE) continue; // déjà la valeur par défaut

            // ordre d'index standard Minecraft : y majeur, puis z, puis x
            int lx = i & 0xF;
            int lz = (i >> 4) & 0xF;
            int ly = (i >> 8) & 0xF;

            int idx = cellIndex(lx, sectionBaseY + ly, lz);
            flags[idx] = flag;
            if (BlockClassifier.isStandable(flag) || BlockClassifier.isClimbable(flag)) {
                names[idx] = paletteNames[paletteIndex];
            }
        }
    }

    private int cellIndex(int x, int y, int z) {
        return ((y - minY) * 16 + z) * 16 + x;
    }

    // ---- requêtes de praticabilité ----

    private boolean occupiableAt(int x, int y, int z) {
        if (y > maxY) return true;  // au-dessus du monde généré : ciel ouvert
        if (y < minY) return false; // sous le monde généré : pas de données fiables
        return BlockClassifier.isOccupiable(flags[cellIndex(x, y, z)]);
    }

    private boolean standableAt(int x, int y, int z) {
        if (y < minY || y > maxY) return false;
        return BlockClassifier.isStandable(flags[cellIndex(x, y, z)]);
    }

    private boolean climbableAt(int x, int y, int z) {
        if (y < minY || y > maxY) return false;
        return BlockClassifier.isClimbable(flags[cellIndex(x, y, z)]);
    }

    private String floorNameAt(int x, int y, int z) {
        if (y < minY || y > maxY) return "minecraft:stone";
        return names[cellIndex(x, y, z)];
    }

    /**
     * Praticable soit normalement (sol praticable dessous), soit en grimpant (le bloc
     * lui-même est une échelle/liane, qui sert alors de point d'appui vertical sans avoir
     * besoin d'un sol).
     */
    private boolean isWalkable(int x, int y, int z) {
        if (x < 0 || x > 15 || z < 0 || z > 15) return false;
        if (!occupiableAt(x, y, z) || !occupiableAt(x, y + 1, z)) return false;
        return standableAt(x, y - 1, z) || climbableAt(x, y, z);
    }

    // ---- construction du graphe ----

    private void buildWalkableNodes(PathCostRules costRules) {
        List<Integer> xs = new ArrayList<>();
        List<Integer> ys = new ArrayList<>();
        List<Integer> zs = new ArrayList<>();
        List<Integer> costs = new ArrayList<>();

        for (int y = minY; y <= maxY; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (!isWalkable(x, y, z)) continue;
                    int idx = cellIndex(x, y, z);
                    nodeIndexGrid[idx] = xs.size();
                    xs.add(x);
                    ys.add(y);
                    zs.add(z);

                    // Coût du sol si un sol existe (cas normal, y compris à la base d'une
                    // échelle posée au sol) ; sinon, en pleine grimpe sans sol dessous, coût
                    // de l'échelle/liane elle-même — défini par les mêmes règles de coût
                    // (PathCostRules) que le reste, juste indexé par le nom du bloc grimpé
                    // plutôt que par le nom du bloc sous les pieds.
                    if (standableAt(x, y - 1, z)) {
                        String floorName = floorNameAt(x, y - 1, z);
                        costs.add(floorName == null ? costRules.costOf("minecraft:stone") : costRules.costOf(floorName));
                    } else {
                        String climbName = names[idx];
                        costs.add(climbName == null ? costRules.costOf("minecraft:ladder") : costRules.costOf(climbName));
                    }
                }
            }
        }

        nodeCount = xs.size();
        nodeX = new int[nodeCount];
        nodeY = new int[nodeCount];
        nodeZ = new int[nodeCount];
        nodeCost = new int[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            nodeX[i] = xs.get(i);
            nodeY[i] = ys.get(i);
            nodeZ[i] = zs.get(i);
            nodeCost[i] = costs.get(i);
        }
    }

    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};

    private void buildAdjacency() {
        neighborIds = new int[nodeCount][];
        neighborCosts = new int[nodeCount][];
        int[] tmpIds = new int[14]; // 12 horizontaux + jusqu'à 2 arcs de grimpe verticale
        int[] tmpCosts = new int[14];

        for (int i = 0; i < nodeCount; i++) {
            int x = nodeX[i], y = nodeY[i], z = nodeZ[i];
            int n = 0;
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d], nz = z + DZ[d];
                if (nx < 0 || nx > 15 || nz < 0 || nz > 15) continue; // hors chunk -> géré par les portes
                for (int dy = -GATE_UP_DOWN; dy <= GATE_UP_DOWN; dy++) {
                    int ny = y + dy;
                    if (!isWalkable(nx, ny, nz)) continue;
                    int neighborIdx = nodeIndexGrid[cellIndex(nx, ny, nz)];
                    if (neighborIdx < 0) continue;
                    tmpIds[n] = neighborIdx;
                    tmpCosts[n] = nodeCost[neighborIdx];
                    n++;
                }
            }

            // Grimpe verticale (même x/z) : seulement possible si CE noeud est une
            // échelle/liane, exactement comme en jeu (on ne peut pas monter/descendre tout
            // droit sans s'accrocher à quelque chose). Le coût réutilise nodeCost du noeud
            // d'arrivée, comme pour tous les autres arcs.
            if (climbableAt(x, y, z)) {
                for (int dy = -1; dy <= 1; dy += 2) {
                    int ny = y + dy;
                    if (!isWalkable(x, ny, z)) continue;
                    int neighborIdx = nodeIndexGrid[cellIndex(x, ny, z)];
                    if (neighborIdx < 0) continue;
                    tmpIds[n] = neighborIdx;
                    tmpCosts[n] = nodeCost[neighborIdx];
                    n++;
                }
            }

            neighborIds[i] = Arrays.copyOf(tmpIds, n);
            neighborCosts[i] = Arrays.copyOf(tmpCosts, n);
        }
    }

    // ---- accès public ----

    public int nodeCount() {
        return nodeCount;
    }

    public int x(int node) {
        return nodeX[node];
    }

    public int y(int node) {
        return nodeY[node];
    }

    public int z(int node) {
        return nodeZ[node];
    }

    public int cost(int node) {
        return nodeCost[node];
    }

    /**
     * Index du noeud praticable exact à cette position (coordonnées locales 0-15 en x/z),
     * ou -1 si cette position n'est pas praticable (ou hors du chunk / de la plage Y connue).
     */
    public int nodeAt(int x, int y, int z) {
        if (x < 0 || x > 15 || z < 0 || z > 15 || y < minY || y > maxY) return -1;
        return nodeIndexGrid[cellIndex(x, y, z)];
    }

    /** Noeuds praticables situés sur le bord donné du chunk. */
    public List<Integer> boundaryNodes(Direction dir) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            boolean onEdge = switch (dir) {
                case WEST -> nodeX[i] == 0;
                case EAST -> nodeX[i] == 15;
                case NORTH -> nodeZ[i] == 0;
                case SOUTH -> nodeZ[i] == 15;
            };
            if (onEdge) result.add(i);
        }
        return result;
    }

    /** Étiquette chaque noeud avec l'id de sa composante connexe interne au chunk. */
    public int[] labelComponents() {
        int[] comp = new int[nodeCount];
        Arrays.fill(comp, -1);
        int current = 0;
        int[] stack = new int[nodeCount];
        for (int start = 0; start < nodeCount; start++) {
            if (comp[start] != -1) continue;
            int sp = 0;
            stack[sp++] = start;
            comp[start] = current;
            while (sp > 0) {
                int u = stack[--sp];
                for (int v : neighborIds[u]) {
                    if (comp[v] == -1) {
                        comp[v] = current;
                        stack[sp++] = v;
                    }
                }
            }
            current++;
        }
        return comp;
    }

    /**
     * Dijkstra borné depuis un seul noeud. Renvoie les distances (Integer.MAX_VALUE si
     * non atteint ou au-delà de `cutoff`). `cutoff` = Integer.MAX_VALUE pour désactiver
     * la limite.
     */
    public int[] dijkstraFrom(int start, int cutoff) {
        return dijkstraMultiSource(new int[]{start}, cutoff);
    }

    /** Dijkstra multi-source : toutes les sources partent à distance 0. */
    public int[] dijkstraMultiSource(int[] starts, int cutoff) {
        int[] dist = new int[nodeCount];
        Arrays.fill(dist, Integer.MAX_VALUE);
        PriorityQueue<long[]> pq = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
        for (int s : starts) {
            if (dist[s] != 0) {
                dist[s] = 0;
                pq.add(new long[]{0, s});
            }
        }
        while (!pq.isEmpty()) {
            long[] top = pq.poll();
            int d = (int) top[0];
            int u = (int) top[1];
            if (d > dist[u]) continue;
            if (d > cutoff) continue;
            int[] ids = neighborIds[u];
            int[] costs = neighborCosts[u];
            for (int k = 0; k < ids.length; k++) {
                int v = ids[k];
                long nd = (long) d + costs[k];
                if (nd <= cutoff && nd < dist[v]) {
                    dist[v] = (int) nd;
                    pq.add(new long[]{nd, v});
                }
            }
        }
        return dist;
    }
}
