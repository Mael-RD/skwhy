package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.doc.RequiredPlugins;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.DisplayData;

@Name("Display Scale / Translation")
@Description("Gets or sets the scale vector or translation offset of fake display entities. Scale controls the size on each axis; translation offsets the display relative to its anchor point. Targets can be one or more displays or a display group.")
@Examples({
    "set {_display} to a new fake item display:",
    "    item: dirt",
    "set {_group} to a new fake display group at player from {_display}",
    "",
    "# Read the current scale",
    "set {_scale} to fake display scale of {_display}",
    "",
    "# Set the scale to double on all axes",
    "set fake display scale of {_display} to vector(2, 2, 2)",
    "set fake display scale of {_group} to vector(2, 2, 2)",
    "",
    "# Read the current translation offset",
    "set {_offset} to fake display translation of {_display}",
    "",
    "# Shift the display upward by 1 block",
    "set fake display translation of {_display} to vector(0, 1, 0)"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class DisplayScale extends SimpleExpression<Vector> {

    private int matchedPattern;
    private boolean pluralTargets;
    private Expression<?> targets;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.pluralTargets = matchedPattern >= 2;
        this.matchedPattern = matchedPattern % 2;
        targets = exprs[0];
        return true;
    }

    @Override
    protected @Nullable Vector[] get(Event event) {
        if (pluralTargets) return null;
        Object value = targets.getSingle(event);
        if (!(value instanceof DisplayData display)) return null;
        Vector vector = switch (matchedPattern) {
            case 0 -> display.getScale().toVector();
            case 1 -> display.getTranslation().toVector();
            default -> null;
        };
        return vector == null ? null : new Vector[]{ new Vector(vector.getX(), vector.getY(), vector.getZ()) };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ Vector.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || !(delta[0] instanceof Vector v)) return;
        for (DisplayData display : DisplayExpressionTargets.resolveForChange(targets, event, pluralTargets)) {
            switch (matchedPattern) {
                case 0 -> display.setScale((float) v.getX(), (float) v.getY(), (float) v.getZ());
                case 1 -> display.setTranslation((float) v.getX(), (float) v.getY(), (float) v.getZ());
            }
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Vector> getReturnType() { return Vector.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "fake display transformation of " + targets.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(DisplayScale.class, Vector.class)
                .addPattern("[the] fake display scale of %displaydata/displaygroup%")           // 0
                .addPattern("[the] fake display translation of %displaydata/displaygroup%")     // 1
                .addPattern("[the] fake display scale of %displaydatas/displaygroups%")           // 2
                .addPattern("[the] fake display translation of %displaydatas/displaygroups%")     // 3
                .build()
        );
    }
}
