package skwhy.modules.FakeDisplayElements.expressions;

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

public class DisplayScale extends SimpleExpression<Vector> {

    private Expression<DisplayData> displayExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        displayExpr = (Expression<DisplayData>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Vector[] get(Event event) {
        DisplayData d = displayExpr.getSingle(event);
        if (d == null) return null;
        com.github.retrooper.packetevents.util.Vector3f scale = d.getScale();
        return new Vector[]{ new Vector(scale.x, scale.y, scale.z) };
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
        d.setScale((float) v.getX(), (float) v.getY(), (float) v.getZ());
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
                .addPattern("[the] scale of %displaydata%")
                .build()
        );
    }
}
