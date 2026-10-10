package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.doc.RequiredPlugins;

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

@Name("Display Item")
@Description("Gets or sets the item displayed by item displays. Targets can be one or more displays or a display group. Accepts ItemType or ItemStack on set.")
@Examples({
    "set {_group} to a new fake display group at player",
    "set {_display} to [a new fake item display]:",
    "    set item of display to dirt",
    "",
    "# Read the current item",
    "set {_item} to item of {_display}",
    "",
    "# Change the displayed item",
    "set item of {_display} to diamond sword",
    "",
    "# Change using item stack expression",
    "set itemstack of {_display} to stone"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class DisplayItem extends SimpleExpression<ItemType> {

    private Expression<?> targets;
    private boolean pluralTargets;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        pluralTargets = matchedPattern >= 1;
        targets = exprs[0];
        return true;
    }

    @Override
    protected @Nullable ItemType[] get(Event event) {
        if (pluralTargets) return null;
        Object value = targets.getSingle(event);
        if (!(value instanceof ItemDisplayData itemDisplay)) return null;
        String raw = itemDisplay.getItemStack();
        if (raw == null || raw.isBlank()) return null;
        ItemType parsed = Classes.parse(raw, ItemType.class, ParseContext.DEFAULT);
        if (parsed != null) return new ItemType[]{ parsed };
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
        for (DisplayData display : DisplayExpressionTargets.resolveForChange(targets, event, pluralTargets)) {
            if (!(display instanceof ItemDisplayData itemDisplay)) continue;
            if (delta[0] instanceof ItemType itemType) {
                itemDisplay.setItemStack(itemType.getMaterial().name());
            } else if (delta[0] instanceof ItemStack itemStack) {
                itemDisplay.setItemStack(itemStack.getType().name());
            }
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends ItemType> getReturnType() { return ItemType.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "item of " + targets.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(DisplayItem.class, ItemType.class)
                .addPattern("[the] item[[ ]stack] of %displaydata/displaygroup%")
                .addPattern("[the] item[[ ]stack] of %displaydatas/displaygroups%")
                .build()
        );
    }
}
