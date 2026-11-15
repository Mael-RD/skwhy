package skwhy.modules.LongNavigationElements.expressions;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.chunkpath.longnav.LongNavigation;

@Name("[Long Navigation] Path Smoothing")
@Description("Smoothing of the position a long navigation makes its entity follow, in blocks (0 by default = off, the entity walks " +
    "from node to node). Above 0 the walk follows a curve of the path computed with the same algorithm as the " +
    "'smoothed locations' expression (x and z only, the height stays the one of the path): the corners are rounded and a " +
    "staircase of nodes becomes a straight line, the higher the value the smoother. The curve does not go through every node. " +
    "Where the hitbox of the entity does not fit on the curve (a wall, a fence, a ledge) the entity walks the plain path, and " +
    "goes back to the curve as soon as its hitbox fits again. A negative value counts as 0. Resetting gives back 0.")
@Examples({
    "set path smoothing of long navigation {_longnav} to 4",
    "set path smoothing of long navigation {_longnav} to 0",
    "reset path smoothing of long navigation {_longnav}"
})
@Since("1.6.0")
public class LongNavigationPathSmoothing extends SimpleExpression<Number> {

    private Expression<LongNavigation> navigationExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.navigationExpr = (Expression<LongNavigation>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Number[] get(Event event) {
        LongNavigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return null;
        return new Number[]{ navigation.getPathSmoothing() };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ Number.class };
        if (mode == ChangeMode.RESET) return new Class<?>[0];
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        LongNavigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return;
        if (mode == ChangeMode.RESET) {
            navigation.setPathSmoothing(0);
        } else if (mode == ChangeMode.SET && delta != null && delta.length > 0 && delta[0] instanceof Number n) {
            double blocks = n.doubleValue();
            navigation.setPathSmoothing(Double.isNaN(blocks) ? 0 : Math.max(0, Math.min(1000, blocks)));
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Number> getReturnType() { return Number.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "path smoothing of " + navigationExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(LongNavigationPathSmoothing.class, Number.class)
                .addPattern("path smoothing of long navigation %longnavigation%")
                .build()
        );
    }
}
