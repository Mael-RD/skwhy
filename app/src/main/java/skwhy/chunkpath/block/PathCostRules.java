package skwhy.chunkpath.block;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coût de déplacement selon le bloc SOUS les pieds (le bloc sur lequel on marche).
 * Coût par défaut = 5. La première règle qui matche (dans l'ordre de la liste) gagne.
 *
 * Exemple d'utilisation :
 * <pre>
 *   PathCostRules rules = new PathCostRules(List.of(
 *       new CostRule(List.of(Material.STONE_BRICKS, Material.GRAVEL_PATH), List.of(), 1),
 *       new CostRule(List.of(), List.of(Tag.LEAVES), 8)
 *   ), 5);
 * </pre>
 */
public final class PathCostRules {

    /** Une règle : coût appliqué si le bloc est dans `materials` OU tagué par un des `tags`. */
    public record CostRule(List<Material> materials, List<Tag<Material>> tags, int cost) {
        boolean matches(Material mat) {
            if (mat == null) return false;
            if (materials.contains(mat)) return true;
            for (Tag<Material> tag : tags) {
                if (tag.isTagged(mat)) return true;
            }
            return false;
        }
    }

    private final List<CostRule> rules;
    private final int defaultCost;
    private final ConcurrentHashMap<String, Integer> cache = new ConcurrentHashMap<>();

    public PathCostRules(List<CostRule> rules, int defaultCost) {
        this.rules = rules;
        this.defaultCost = defaultCost;
    }

    public static PathCostRules defaults() {
        return new PathCostRules(List.of(), 5);
    }

    public int costOf(String blockName) {
        return cache.computeIfAbsent(blockName, this::computeCost);
    }

    /** Coût appliqué quand aucune règle ne matche. Sert aussi de coût par défaut pour le franchissement d'une frontière de chunk (cf. {@code skwhy.chunkpath.pathfinding}), aucun coût de sol n'étant stocké pour cette traversée. */
    public int defaultCost() {
        return defaultCost;
    }

    private int computeCost(String blockName) {
        Material mat = BlockClassifier.resolveMaterial(blockName);
        for (CostRule rule : rules) {
            if (rule.matches(mat)) {
                return rule.cost();
            }
        }
        return defaultCost;
    }
}
