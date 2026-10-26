package skwhy.chunkpath.block;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coût de déplacement selon le bloc SOUS les pieds (le bloc sur lequel on marche).
 * La première règle qui matche (dans l'ordre de la liste) gagne. Une règle sans bloc ni tag est la
 * règle par défaut (la première de la liste, si plusieurs) : elle s'applique aux blocs qu'aucune autre
 * règle ne couvre. Sans règle par défaut, un bloc non couvert est non marchable ({@link #BLOCKED}).
 *
 * Exemple d'utilisation :
 * <pre>
 *   PathCostRules rules = new PathCostRules(List.of(
 *       new CostRule(List.of(Material.STONE_BRICKS, Material.GRAVEL_PATH), List.of(), 1),
 *       new CostRule(List.of(), List.of(Tag.LEAVES), 8),
 *       new CostRule(List.of(), List.of(), 5) // règle par défaut
 *   ));
 * </pre>
 */
public final class PathCostRules {

    /** Coût renvoyé par {@link #costOf} pour un bloc non marchable. */
    public static final int BLOCKED = -1;

    /**
     * Une règle : coût appliqué si le bloc est dans `materials` OU tagué par un des `tags`.
     * {@code catchAll} : règle par défaut (aucun bloc ni tag demandé), qui ne matche jamais directement.
     */
    public record CostRule(List<Material> materials, List<Tag<Material>> tags, int cost, boolean catchAll) {
        public CostRule(List<Material> materials, List<Tag<Material>> tags, int cost) {
            this(materials, tags, cost, materials.isEmpty() && tags.isEmpty());
        }

        /** Identité structurelle (blocs, noms de tags, coût, règle par défaut) : deux copies d'une même règle ont la même. */
        public String signature() {
            List<String> blocks = new java.util.ArrayList<>();
            for (Material m : materials) blocks.add(m.name());
            java.util.Collections.sort(blocks);
            List<String> tagKeys = new java.util.ArrayList<>();
            for (Tag<Material> t : tags) tagKeys.add(String.valueOf(t.getKey()));
            java.util.Collections.sort(tagKeys);
            return blocks + "|" + tagKeys + "|" + cost + "|" + catchAll;
        }

        boolean matches(Material mat) {
            if (mat == null || catchAll) return false;
            if (materials.contains(mat)) return true;
            for (Tag<Material> tag : tags) {
                if (tag.isTagged(mat)) return true;
            }
            return false;
        }
    }

    private final List<CostRule> rules;
    private final int defaultCost; // BLOCKED s'il n'y a pas de règle par défaut
    private final ConcurrentHashMap<String, Integer> cache = new ConcurrentHashMap<>();

    public PathCostRules(List<CostRule> rules) {
        this.rules = rules;
        int fallback = BLOCKED;
        for (CostRule rule : rules) {
            if (rule.catchAll()) {
                fallback = rule.cost();
                break;
            }
        }
        this.defaultCost = fallback;
    }

    /** @return le coût du bloc, ou {@link #BLOCKED} s'il est non marchable. */
    public int costOf(String blockName) {
        return cache.computeIfAbsent(blockName, this::computeCost);
    }

    private static final int UNSET = Integer.MIN_VALUE;
    private final int[] byMaterial = newUnsetTable();

    private static int[] newUnsetTable() {
        int[] table = new int[Material.values().length];
        java.util.Arrays.fill(table, UNSET);
        return table;
    }

    /** Comme {@link #costOf(String)} sans construire de nom : pour les recherches de chemin qui lisent des milliers de blocs. */
    public int costOf(Material material) {
        int cost = byMaterial[material.ordinal()];
        if (cost == UNSET) {
            cost = costOf(material.getKey().toString());
            byMaterial[material.ordinal()] = cost;
        }
        return cost;
    }

    /** Les règles, dans l'ordre d'évaluation (copie non modifiable). */
    public List<CostRule> rules() {
        return List.copyOf(rules);
    }

    /** Coût de la règle par défaut, ou {@link #BLOCKED} s'il n'y en a pas. */
    public int defaultCost() {
        return defaultCost;
    }

    private int computeCost(String blockName) {
        Material mat = BlockClassifier.resolveMaterial(blockName);
        for (CostRule rule : rules) {
            if (rule.matches(mat)) {
                return rule.cost() < 0 ? BLOCKED : rule.cost();
            }
        }
        return defaultCost;
    }
}
