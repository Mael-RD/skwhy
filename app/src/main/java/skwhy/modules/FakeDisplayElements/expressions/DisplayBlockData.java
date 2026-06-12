package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.doc.RequiredPlugins;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.BlockDisplayData;
import skwhy.data.DisplayData;

@Name("Display Block Data")
@Description("Gets or sets the block data string of a block display entity. Only works on DisplayData instances that are of the BlockDisplay type.")
@Examples({
    "set {_group} to a new fake display group at player",
    "set {_display} to [a new fake item display]:",
    "    set item of display to dirt",
    "",
    "# Read the block data",
    "set {_data} to block data of {_display}",
    "",
    "# Change the displayed block",
    "set blockdata of {_display} to stone"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class DisplayBlockData extends SimpleExpression<BlockData> {

    private Expression<DisplayData> displayExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        displayExpr = (Expression<DisplayData>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable BlockData[] get(Event event) {
        DisplayData d = displayExpr.getSingle(event);
        if (!(d instanceof BlockDisplayData block)) return null;

        String raw = block.getBlockData();
        if (raw == null || raw.isBlank()) return null;

        try {
            return new BlockData[]{ Bukkit.createBlockData(raw) };
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ BlockData.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || !(delta[0] instanceof BlockData bd)) return;
        DisplayData d = displayExpr.getSingle(event);
        if (!(d instanceof BlockDisplayData block)) return;
        // getAsString(false) = sans namespace si possible, true = format complet
        block.setBlockData(bd.getAsString(false));
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends BlockData> getReturnType() { return BlockData.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "block data of " + displayExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(DisplayBlockData.class, BlockData.class)
                .addPattern("[the] block[ ]data of %displaydata%")
                .build()
        );
    }
}
