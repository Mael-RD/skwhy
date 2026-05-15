package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.registrations.Classes;
import ch.njol.util.Kleenean;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.DisplayData;
import skwhy.data.ItemDisplayData;

public class DisplayItem extends SimpleExpression<ItemType> {

    private Expression<DisplayData> displayExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        displayExpr = (Expression<DisplayData>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable ItemType[] get(Event event) {
        DisplayData d = displayExpr.getSingle(event);
        if (!(d instanceof ItemDisplayData itemDisplay)) return null;

        String raw = itemDisplay.getItemStack();
        if (raw == null || raw.isBlank()) return null;

        // Essaie de parser via Skript (supporte "diamond sword", "DIAMOND_SWORD", etc.)
        ItemType parsed = Classes.parse(raw, ItemType.class, ParseContext.DEFAULT);
        if (parsed != null) return new ItemType[]{ parsed };

        // Fallback : parse par nom de matériau Bukkit
        try {
            Material mat = Material.valueOf(raw.toUpperCase().replace(" ", "_").replace("MINECRAFT:", ""));
            return new ItemType[]{ new ItemType(new ItemStack(mat)) };
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ ItemType.class, ItemStack.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || delta[0] == null) return;
        DisplayData d = displayExpr.getSingle(event);
        if (!(d instanceof ItemDisplayData itemDisplay)) return;

        if (delta[0] instanceof ItemType itemType) {
            // ItemType → nom du matériau
            Material mat = itemType.getMaterial();
            itemDisplay.setItemStack(mat.name());
        } else if (delta[0] instanceof ItemStack itemStack) {
            itemDisplay.setItemStack(itemStack.getType().name());
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends ItemType> getReturnType() { return ItemType.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "item of " + displayExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(DisplayItem.class, ItemType.class)
                .addPattern("[the] item[[ ]stack] of %displaydata%")
                .build()
        );
    }
}
