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
@Description("Gets or sets the block data of block displays. Targets can be one or more displays or a display group.")
@Examples({
    "set {_group} to a new fake display group at player",
    "set {_display} to a new fake item display:",
    "    item: dirt",
    "",
    "# Read the block data",
    "set {_data} to fake display block data of {_display}",
    "",
    "# Change the displayed block",
    "set fake display blockdata of {_display} to stone"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class DisplayBlockData extends SimpleExpression<BlockData> {

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
    protected @Nullable BlockData[] get(Event event) {
        if (pluralTargets) return null;
        Object value = targets.getSingle(event);
        if (!(value instanceof BlockDisplayData block)) return null;
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
        for (DisplayData display : DisplayExpressionTargets.resolveForChange(targets, event, pluralTargets)) {
            if (display instanceof BlockDisplayData block) {
                block.setBlockData(bd.getAsString(false));
            }
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends BlockData> getReturnType() { return BlockData.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "block data of " + targets.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(DisplayBlockData.class, BlockData.class)
                .addPattern("[the] fake display block[ ]data of %displaydata/displaygroup%")
                .addPattern("[the] fake display block[ ]data of %displaydatas/displaygroups%")
                .build()
        );
    }
}
