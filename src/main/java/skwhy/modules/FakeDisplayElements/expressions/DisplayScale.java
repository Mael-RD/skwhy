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
@Description("Gets or sets the scale vector or translation offset of a fake display entity. Scale controls the size on each axis; translation offsets the display relative to its anchor point.")
@Examples({
    "set {_group} to a new fake display group at player",
    "set {_display} to [a new fake item display]:",
    "    set item of display to dirt",
    "",
    "# Read the current scale",
    "set {_scale} to group scale of {_display}",
    "",
    "# Set the scale to double on all axes",
    "set group scale of {_display} to vector(2, 2, 2)",
    "",
    "# Read the current translation offset",
    "set {_offset} to group translation of {_display}",
    "",
    "# Shift the display upward by 1 block",
    "set group translation of {_display} to vector(0, 1, 0)"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class DisplayScale extends SimpleExpression<Vector> {

    private int matchedPattern;
    private Expression<DisplayData> displayExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.matchedPattern = matchedPattern;
        displayExpr = (Expression<DisplayData>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Vector[] get(Event event) {
        DisplayData d = displayExpr.getSingle(event);
        if (d == null) return null;
        Vector scale = switch (matchedPattern) {
            case 0 -> d.getScale().toVector();
            case 1 -> d.getTranslation().toVector();
            default -> null;
        };
        return scale != null ? new Vector[]{ new Vector(scale.getX(), scale.getY(), scale.getZ()) } : null;
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ Vector.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || !(delta[0] instanceof Vector v)) return;
        DisplayData d = displayExpr.getSingle(event);
        if (d == null) return;
        switch (matchedPattern) {
            case 0 -> d.setScale((float) v.getX(), (float) v.getY(), (float) v.getZ());
            case 1 -> d.setTranslation((float) v.getX(), (float) v.getY(), (float) v.getZ());
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Vector> getReturnType() { return Vector.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "scale of " + displayExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(DisplayScale.class, Vector.class)
                .addPattern("[the] group scale of %displaydata%")           // 0
                .addPattern("[the] group translation of %displaydata%")     // 1
                .build()
        );
    }
}
