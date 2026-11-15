package skwhy.modules.CustomItemElements.conditions;

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
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import skwhy.customitem.CustomItemIdentity;
import skwhy.customitem.CustomItemIds;
import skwhy.customitem.CustomItemManager;

@Name("[Custom Item] Is Custom Item / Custom Item Is Registered")
@Description("'<item> is a custom item' checks that the item carries a custom item id (whether or not its definition exists); with 'of id <id>' the id must be that one. " +
    "'custom item <id> is registered' checks that a definition 'custom-items/<id>.yml' is loaded.")
@Examples({
    "if tool of player is a custom item:",
    "	send \"Item custom\" to player",
    "if tool of player is a custom item of id \"billet\":",
    "	send \"Un billet\" to player",
    "if custom item \"billet\" is registered:",
    "	send \"La definition existe\" to player",
    "if custom item \"inconnu\" is not registered:",
    "	send \"Pas de definition\" to player"
})
@Since("1.6.0")
public class CondCustomItem extends Condition {

    private int pattern;
    private Expression<?> first;
    private Expression<String> id;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.CONDITION,
            SyntaxInfo.builder(CondCustomItem.class)
                .addPattern("%itemstacks% (is|are) [a] custom item[s] [of id %-string%]")
                .addPattern("%itemstacks% (isn't|is not|aren't|are not) [a] custom item[s] [of id %-string%]")
                .addPattern("custom item %string% (is|are) registered")
                .addPattern("custom item %string% (isn't|is not|aren't|are not) registered")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        pattern = matchedPattern;
        first = exprs[0];
        if (pattern <= 1) id = (Expression<String>) exprs[1];
        setNegated(pattern == 1 || pattern >= 3);
        return true;
    }

    @Override
    public boolean check(Event event) {
        if (pattern >= 2) {
            CustomItemManager manager = CustomItemManager.get();
            String key = (String) first.getSingle(event);
            boolean exists = manager != null && key != null && manager.registry().has(key);
            return exists != isNegated();
        }
        String wanted = id == null || id.getSingle(event) == null ? null : CustomItemIds.normalize(id.getSingle(event));
        return first.check(event, value -> {
            String actual = CustomItemIdentity.idOf((ItemStack) value);
            return actual != null && (wanted == null || wanted.equals(actual));
        }, isNegated());
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        if (pattern >= 2) return "custom item " + first.toString(event, debug) + (isNegated() ? " is not registered" : " is registered");
        return first.toString(event, debug) + (isNegated() ? " is not" : " is") + " a custom item" + (id == null ? "" : " of id " + id.toString(event, debug));
    }
}
