package skwhy.modules.CustomItemElements.expressions;

import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import skwhy.customitem.CustomItemDef;
import skwhy.customitem.CustomItemIdentity;
import skwhy.customitem.CustomItemManager;
import skwhy.quest.Snbt;

@Name("[Custom Item] Custom Item Info")
@Description("Reads what the plugin knows about custom items. " +
    "'custom item id of <item>' is the id stored in the item (custom_data 'item'; an old id written with spaces is read with '_'), not set for an ordinary item. " +
    "'custom item version of <item>' is the version stored in the item (not set without one); the version of the definition is 'custom item definition version of <id>'. " +
    "'custom item parameters of <item>' lists the stored parameters among owner, safe, save, invulnerable and preserve. " +
    "'custom item definition of <id>' is the definition as text (SNBT, readable with 'nbt compound from'); it is not set for an unknown id. " +
    "'custom item ids' lists the ids of every definition, sorted.")
@Examples({
    "if custom item id of tool of player is \"billet\":",
    "\tsend \"Version %custom item version of tool of player%\" to player",
    "set {_params::*} to custom item parameters of tool of player",
    "set {_def} to custom item definition of \"billet\"",
    "send \"%size of custom item ids% items\" to player"
})
@Since("1.6.0")
public class ExprCustomItemInfo extends SimpleExpression<Object> {

    private static final String[] PATTERNS = {
        "custom item id of %itemstack%",
        "custom item version of %itemstack%",
        "custom item param[eter]s of %itemstack%",
        "custom item definition of %string%",
        "custom item definition version of %string%",
        "[all] custom item ids"
    };

    private int pattern;
    private Expression<?> source;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprCustomItemInfo.class, Object.class)
                .addPatterns(PATTERNS)
                .build()
        );
    }

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        pattern = matchedPattern;
        source = exprs.length > 0 ? exprs[0] : null;
        return true;
    }

    @Override
    protected Object @Nullable [] get(Event event) {
        CustomItemManager manager = CustomItemManager.get();
        if (manager == null) return new Object[0];
        if (pattern == 5) return manager.registry().ids().toArray();
        Object value = source.getSingle(event);
        if (value == null) return new Object[0];
        switch (pattern) {
            case 0 -> {
                String id = CustomItemIdentity.idOf((ItemStack) value);
                return id == null ? new Object[0] : new Object[]{ id };
            }
            case 1 -> {
                int version = CustomItemIdentity.versionOf((ItemStack) value);
                return version < 0 ? new Object[0] : new Object[]{ (long) version };
            }
            case 2 -> {
                return CustomItemIdentity.parametersOf((ItemStack) value).toArray();
            }
            default -> {
                CustomItemDef def = manager.registry().get((String) value);
                if (def == null) return new Object[0];
                return pattern == 3 ? new Object[]{ Snbt.write(def.tree()) } : new Object[]{ (long) def.version() };
            }
        }
    }

    @Override
    public boolean isSingle() { return pattern != 2 && pattern != 5; }

    @Override
    public Class<?> getReturnType() { return pattern == 1 || pattern == 4 ? Number.class : String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        String text = PATTERNS[pattern];
        return source == null ? "custom item ids" : text.replace("%itemstack%", source.toString(event, debug)).replace("%string%", source.toString(event, debug)).replace("param[eter]s", "parameters");
    }
}
