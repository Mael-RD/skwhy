package skwhy.modules.LongNavigationElements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.classes.Serializer;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.yggdrasil.Fields;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;

import skwhy.chunkpath.block.PathCostRules;

import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Version modifiable (côté Skript) d'une {@link PathCostRules.CostRule} : une liste de
 * blocs, une liste de noms de tags de blocs (résolus paresseusement en {@link Tag} au
 * moment de {@link #toCostRule()}, pas avant, pour rester tolérant à un nom de tag
 * inconnu de cette version du serveur — il est alors simplement ignoré), et un coût.
 */
public final class PathCostRuleData {

    private final List<Material> blocks = new ArrayList<>();
    private final List<String> tags = new ArrayList<>();
    private int cost;

    public PathCostRuleData() {
    }

    public PathCostRuleData(int cost) {
        this.cost = cost;
    }

    // ── blocs ──

    public List<Material> getBlocks() {
        return new ArrayList<>(blocks);
    }

    public void addBlocks(List<Material> toAdd) {
        if (toAdd == null) return;
        for (Material m : toAdd) {
            if (m != null && !blocks.contains(m)) blocks.add(m);
        }
    }

    public void removeBlocks(List<Material> toRemove) {
        if (toRemove != null) blocks.removeAll(toRemove);
    }

    public void setBlocks(List<Material> newBlocks) {
        blocks.clear();
        addBlocks(newBlocks);
    }

    public void clearBlocks() {
        blocks.clear();
    }

    // ── tags ──

    public List<String> getTags() {
        return new ArrayList<>(tags);
    }

    public void addTags(List<String> toAdd) {
        if (toAdd == null) return;
        for (String t : toAdd) {
            if (t != null && !t.isBlank() && !tags.contains(t)) tags.add(t);
        }
    }

    public void removeTags(List<String> toRemove) {
        if (toRemove != null) tags.removeAll(toRemove);
    }

    public void setTags(List<String> newTags) {
        tags.clear();
        addTags(newTags);
    }

    public void clearTags() {
        tags.clear();
    }

    // ── coût ──

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    /** Convertit vers la règle immuable réellement utilisée par le moteur de coût ({@code skwhy.chunkpath.block}). */
    public PathCostRules.CostRule toCostRule() {
        List<Tag<Material>> resolvedTags = new ArrayList<>();
        for (String tagName : tags) {
            Tag<Material> tag = resolveTag(tagName);
            if (tag != null) resolvedTags.add(tag);
        }
        return new PathCostRules.CostRule(new ArrayList<>(blocks), resolvedTags, cost);
    }

    /** @return le tag de blocs correspondant à ce nom (espace de noms "minecraft" implicite), ou {@code null} si inconnu. */
    public static Tag<Material> resolveTag(String name) {
        if (name == null || name.isBlank()) return null;
        String key = name.trim().toLowerCase(Locale.ROOT);
        NamespacedKey nsKey = key.contains(":") ? NamespacedKey.fromString(key) : NamespacedKey.minecraft(key);
        if (nsKey == null) return null;
        return Bukkit.getTag(Tag.REGISTRY_BLOCKS, nsKey, Material.class);
    }

    @Override
    public String toString() {
        return "PathCostRule[blocks=" + blocks.size() + ", tags=" + tags + ", cost=" + cost + "]";
    }

    // =========================================================================
    // Enregistrement du type Skript
    // =========================================================================

    public static void register() {
        Classes.registerClass(new ClassInfo<>(PathCostRuleData.class, "pathcostrule")
            .name("Path Cost Rule")
            .description(
                "A single pathfinding cost rule: a set of blocks and/or block tags that, when matched, apply a " +
                "given movement cost instead of the world's default cost. Used to build a custom rule set passed " +
                "to 'register chunk path of %chunk%'."
            )
            .usage("Created via 'a new path cost rule [with cost %number%]', then populated via the blocks/tags list expressions.")
            .user("path ?cost ?rules?")
            .examples(
                "set {_rule} to a new path cost rule with cost 1",
                "add stone and cobblestone to blocks of path cost rule {_rule}",
                "add minecraft tag \"leaves\" to tags of path cost rule {_rule}",
                "register chunk path of target block's chunk using rules {_rule}"
            )
            .since("1.4.0")

            .parser(new Parser<>() {
                @Override
                public PathCostRuleData parse(String s, ParseContext context) {
                    return null; // non parsable depuis du texte
                }

                @Override
                public boolean canParse(ParseContext context) {
                    return false;
                }

                @Override
                public String toString(PathCostRuleData rule, int flags) {
                    return rule.toString();
                }

                @Override
                public String toVariableNameString(PathCostRuleData rule) {
                    return rule.toString();
                }
            })

            .serializer(new Serializer<>() {
                @Override
                public Fields serialize(PathCostRuleData rule) {
                    Fields fields = new Fields();

                    List<Material> blocks = rule.getBlocks();
                    fields.putPrimitive("blockCount", blocks.size());
                    for (int i = 0; i < blocks.size(); i++) {
                        fields.putObject("block_" + i, blocks.get(i));
                    }

                    List<String> tags = rule.getTags();
                    fields.putPrimitive("tagCount", tags.size());
                    for (int i = 0; i < tags.size(); i++) {
                        fields.putObject("tag_" + i, tags.get(i));
                    }

                    fields.putPrimitive("cost", rule.getCost());
                    return fields;
                }

                @Override
                public void deserialize(PathCostRuleData rule, Fields f) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public PathCostRuleData deserialize(Fields fields) throws StreamCorruptedException {
                    try {
                        PathCostRuleData rule = new PathCostRuleData();

                        int blockCount = fields.getPrimitive("blockCount", int.class);
                        List<Material> blocks = new ArrayList<>(blockCount);
                        for (int i = 0; i < blockCount; i++) {
                            Object block = fields.getObject("block_" + i);
                            if (block instanceof Material m) blocks.add(m);
                        }
                        rule.setBlocks(blocks);

                        int tagCount = fields.getPrimitive("tagCount", int.class);
                        List<String> tags = new ArrayList<>(tagCount);
                        for (int i = 0; i < tagCount; i++) {
                            Object tag = fields.getObject("tag_" + i);
                            if (tag instanceof String s) tags.add(s);
                        }
                        rule.setTags(tags);

                        rule.setCost(fields.getPrimitive("cost", int.class));
                        return rule;
                    } catch (Exception e) {
                        throw new StreamCorruptedException("Impossible de désérialiser PathCostRuleData : " + e.getMessage());
                    }
                }

                @Override
                public boolean mustSyncDeserialization() {
                    return false;
                }

                @Override
                protected boolean canBeInstantiated() {
                    return false;
                }
            })
        );
    }
}
