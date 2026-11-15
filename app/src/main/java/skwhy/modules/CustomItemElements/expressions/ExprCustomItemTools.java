package skwhy.modules.CustomItemElements.expressions;

import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
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
import skwhy.customitem.CustomItemCounter;
import skwhy.customitem.CustomItemManager;
import skwhy.customitem.CustomItemUpdater;

@Name("[Custom Item] Amount / Update Of Custom Item")
@Description("'amount of custom item <id> in <inventory>' counts the items of that id (every stack, by its stored id, whatever the other tags). " +
    "'updated custom item <item>' is the item brought to the version of its definition (name, lore, attributes, equippable, effects, bijou and armor_id are taken again from the definition; enchantments never go down; amount, damage and other tags are kept). " +
    "It is the item itself when it is already up to date, is not a custom item or its definition no longer exists. Nothing is changed in the original item.")
@Examples({
    "if amount of custom item \"billet\" in inventory of player >= 3:",
    "\tsend \"Assez de billets\" to player",
    "set tool of player to updated custom item tool of player"
})
@Since("1.6.0")
public class ExprCustomItemTools extends SimpleExpression<Object> {

    private int pattern;
    private Expression<?> first;
    private Expression<Inventory> inventory;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprCustomItemTools.class, Object.class)
                .addPattern("amount of custom item %string% in %inventory%")
                .addPattern("updated custom item %itemstack%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        pattern = matchedPattern;
        first = exprs[0];
        if (pattern == 0) inventory = (Expression<Inventory>) exprs[1];
        return true;
    }

    @Override
    protected Object @Nullable [] get(Event event) {
        CustomItemManager manager = CustomItemManager.get();
        Object value = first.getSingle(event);
        if (value == null) return new Object[0];
        if (pattern == 0) {
            Inventory inv = inventory.getSingle(event);
            return inv == null ? new Object[0] : new Object[]{ (long) CustomItemCounter.count(inv, (String) value) };
        }
        ItemStack stack = (ItemStack) value;
        ItemStack updated = manager == null ? null : CustomItemUpdater.update(stack, manager.registry());
        return new Object[]{ updated == null ? stack : updated };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<?> getReturnType() { return pattern == 0 ? Number.class : ItemStack.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return pattern == 0
            ? "amount of custom item " + first.toString(event, debug) + " in " + inventory.toString(event, debug)
            : "updated custom item " + first.toString(event, debug);
    }
}
