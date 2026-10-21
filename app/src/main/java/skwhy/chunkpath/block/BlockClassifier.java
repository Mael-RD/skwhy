package skwhy.chunkpath.block;

import org.bukkit.Material;
import org.bukkit.Tag;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Traduit un nom de bloc (tel que stocké dans la palette NBT, ex: "minecraft:oak_fence")
 * en deux propriétés utiles au pathfinding :
 *
 *  - occupiable  : un joueur peut occuper cet espace (air, cave_air, portes, tapis...)
 *  - standable   : ce bloc peut servir de sol sous les pieds (bloc plein normal)
 *
 * Simplifications volontaires (cf. spec) :
 *  - dalles/escaliers/pierre etc. traités comme des blocs pleins classiques (on ignore
 *    leur géométrie réelle, isSolid() suffit)
 *  - clôtures/murs/portillons : non praticables ET on ne peut pas se tenir dessus
 *  - trappes : jamais praticables, quel que soit leur état ouvert/fermé
 *  - portes : toujours traversables (on ignore l'état ouvert/fermé)
 *  - eau et lave : bloquées (ni occupables, ni un sol valide)
 *  - échelles/lianes (Tag.CLIMBABLE) : occupables ET grimpables — un noeud grimpable n'a
 *    pas besoin d'un sol praticable dessous (cf. ChunkNavMesh#isWalkable), il se suffit à
 *    lui-même comme point d'appui vertical
 *  - bloc inconnu (non résolu en Material) : traité par défaut comme un bloc plein
 *    praticable-dessus (hypothèse la plus sûre pour ne pas faire "tomber" le pathfinding
 *    dans du vide sur un bloc futur/modded non reconnu)
 */
public final class BlockClassifier {

    public static final byte OCCUPIABLE = 0b001;
    public static final byte STANDABLE = 0b010;
    public static final byte CLIMBABLE = 0b100;

    /**
     * Blocks flagged solid by {@link Material#isSolid()} yet without any collision box (pressure plates,
     * buttons...): nothing to stand on, nothing to bump into. Filled by {@link #initCollisionData()}.
     */
    private static volatile Set<Material> noCollision = Collections.emptySet();

    private static final ConcurrentHashMap<String, Byte> FLAGS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Material> MATERIAL_CACHE = new ConcurrentHashMap<>();

    private BlockClassifier() {
    }

    /**
     * Reads, on the main thread, which solid blocks have no collision box in their default state (the
     * block data API needs a world). Must be called once worlds exist; the classification cache is reset.
     */
    public static void initCollisionData() {
        if (Bukkit.getWorlds().isEmpty()) return;
        World world = Bukkit.getWorlds().get(0);
        Location origin = new Location(world, 0, world.getMinHeight(), 0);
        Set<Material> result = EnumSet.noneOf(Material.class);
        for (Material mat : Material.values()) {
            if (!mat.isBlock() || mat.isLegacy() || mat.isAir() || !mat.isSolid()) continue;
            try {
                if (mat.createBlockData().getCollisionShape(origin).getBoundingBoxes().isEmpty()) {
                    result.add(mat);
                }
            } catch (RuntimeException ignored) {
                // a block whose data cannot be created without a state: keep the isSolid() answer
            }
        }
        noCollision = result;
        FLAGS_CACHE.clear();
    }

    public static Set<Material> noCollisionBlocks() {
        return noCollision;
    }

    public static byte classify(String blockName) {
        return FLAGS_CACHE.computeIfAbsent(blockName, BlockClassifier::computeFlags);
    }

    public static Material resolveMaterial(String blockName) {
        return MATERIAL_CACHE.computeIfAbsent(blockName, Material::matchMaterial);
    }

    private static byte computeFlags(String blockName) {
        Material mat = resolveMaterial(blockName);

        if (mat == null) {
            // Bloc non reconnu par cette version du serveur : hypothèse conservative.
            return STANDABLE;
        }
        if (mat == Material.WATER || mat == Material.LAVA) {
            return 0; // ni occupable ni sol : passage bloqué
        }
        if (Tag.CLIMBABLE.isTagged(mat)) {
            return OCCUPIABLE | CLIMBABLE; // échelle, liane... : occupable, et grimpable sans sol requis
        }
        if (Tag.TRAPDOORS.isTagged(mat)) {
            return 0; // jamais praticable, ni comme sol ni comme espace traversable
        }
        if (Tag.DOORS.isTagged(mat)) {
            return OCCUPIABLE; // toujours traversable, jamais un "sol"
        }
        if (Tag.FENCES.isTagged(mat) || Tag.WALLS.isTagged(mat) || Tag.FENCE_GATES.isTagged(mat)) {
            return 0; // bloque le passage, et on ne marche pas dessus non plus
        }
        if (!mat.isSolid() || noCollision.contains(mat)) {
            return OCCUPIABLE; // air, cave air, herbes, fleurs, torches, plaques de pression, boutons, etc.
        }
        return STANDABLE; // bloc plein "normal" (pierre, dalle, escalier considéré plein...)
    }

    public static boolean isOccupiable(byte flags) {
        return (flags & OCCUPIABLE) != 0;
    }

    public static boolean isStandable(byte flags) {
        return (flags & STANDABLE) != 0;
    }

    public static boolean isClimbable(byte flags) {
        return (flags & CLIMBABLE) != 0;
    }
}
