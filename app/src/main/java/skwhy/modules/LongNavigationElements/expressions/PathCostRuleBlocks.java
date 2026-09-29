package skwhy.modules.LongNavigationElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.SkWhy;
import skwhy.modules.LongNavigationElements.types.PathCostRuleData;

import java.util.ArrayList;
import java.util.List;

@Name("[ChunkPath] Path Cost Rule Blocks")
@Description("Gets, sets, adds to, removes from, or clears the list of exact blocks matched by a path cost rule " +
    "(the rule's cost applies to a chunk position when its floor block is one of these blocks, or tagged by one " +
    "of its tags). 'set'/'add'/'remove' accept a material, a physical block, an item stack, or an item type — " +
    "all of them are converted to (and stored as) a plain material, exactly like before. Giving an item that " +
    "can't exist as a placed block (e.g. a snowball) logs a console warning since it will then never match a " +
    "chunk's floor block. 'delete' and 'reset' both empty the list; the rule's cost itself is untouched by either.")
@Examples({
    "set {_rule} to a new path cost rule with cost 1",
    "add stone and cobblestone to blocks of path cost rule {_rule}",
    "add target block to blocks of path cost rule {_rule}",
    "add player's tool to blocks of path cost rule {_rule}",
    "remove cobblestone from blocks of path cost rule {_rule}",
    "set blocks of path cost rule {_rule} to stone and deepslate",
    "delete blocks of path cost rule {_rule}"
})
@Since("1.4.0")
public class PathCostRuleBlocks extends SimpleExpression<Material> {

    private Expression<PathCostRuleData> ruleExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.ruleExpr = (Expression<PathCostRuleData>) exprs[0];
        return true;
    }

    @Override
    protected Material @Nullable [] get(Event event) {
        PathCostRuleData[] rules = ruleExpr.getAll(event);
        if (rules == null || rules.length == 0) return null;
        List<Material> result = new ArrayList<>();
        for (PathCostRuleData rule : rules) {
            if (rule != null) result.addAll(rule.getBlocks());
        }
        return result.toArray(new Material[0]);
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case SET, ADD, REMOVE ->
                new Class<?>[]{ Material.class, Block.class, ItemStack.class, ItemType.class,
                    Material[].class, Block[].class, ItemStack[].class, ItemType[].class };
            case DELETE, RESET -> new Class<?>[0];
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        PathCostRuleData[] rules = ruleExpr.getAll(event);
        if (rules == null) return;

        List<Material> values = toMaterialList(delta);
        for (PathCostRuleData rule : rules) {
            if (rule == null) continue;
            switch (mode) {
                case SET -> rule.setBlocks(values);
                case ADD -> rule.addBlocks(values);
                case REMOVE -> rule.removeBlocks(values);
                case DELETE, RESET -> rule.clearBlocks();
                default -> {
                }
            }
        }
    }

    private static List<Material> toMaterialList(Object @Nullable [] delta) {
        List<Material> list = new ArrayList<>();
        if (delta == null) return list;
        for (Object o : delta) {
            if (o instanceof Object[] batch) {
                // Skript may hand back a batch as a single array element instead of converting one by one
                // (see the "*[].class" entries in acceptChange) — flatten it either way.
                for (Object element : batch) addResolvedMaterial(list, element);
            } else {
                addResolvedMaterial(list, o);
            }
        }
        return list;
    }

    private static void addResolvedMaterial(List<Material> list, @Nullable Object o) {
        Material material = resolveMaterial(o);
        if (material == null || list.contains(material)) return;
        if (!material.isBlock()) {
            SkWhy.getInstance().getLogger().warning(
                "[ChunkPath] '" + material.name() + "' est un item sans forme de bloc placé (ex. une boule de "
                    + "neige) : il ne correspondra jamais au bloc de sol d'une position de chunk dans une "
                    + "règle de coût.");
        }
        list.add(material);
    }

    /** Convertit toute source de bloc/item courante (bloc physique, pile d'objets, type Skript...) en son matériau. */
    private static @Nullable Material resolveMaterial(@Nullable Object o) {
        if (o instanceof Material m) return m;
        if (o instanceof Block b) return b.getType();
        if (o instanceof ItemStack is) return is.getType();
        if (o instanceof ItemType it) return it.getMaterial();
        return null;
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends Material> getReturnType() { return Material.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "blocks of path cost rule " + ruleExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(PathCostRuleBlocks.class, Material.class)
                .addPattern("blocks of path cost rule %pathcostrules%")
                .build()
        );
    }
}
