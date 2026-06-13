package skwhy.modules.NavigationElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.pathfinder.Navigation;

@Name("[Navigation] Hitbox")
@Description("Gets or sets the hitbox vector of a navigation object. " +
    "The vector defines the collision size used during pathfinding (X = width, Y = height, Z = depth).")
@Examples({
    "set {_navigation} to a new navigation with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player type \"WALK\"",
    "",
    "# Read the current hitbox",
    "set {_hitbox} to hitbox of navigation {_navigation}",
    "",
    "# Resize the hitbox to match a smaller entity",
    "set hitbox of navigation {_navigation} to vector(0.4, 0.9, 0.4)",
    "",
    "# Resize the hitbox to match a larger entity",
    "set hitbox of navigation {_navigation} to vector(1.2, 2.4, 1.2)"
})
@Since("1.2.0")
public class NavigationVector extends SimpleExpression<Vector> {

    private Expression<Navigation> navigationExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.navigationExpr = (Expression<Navigation>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Vector[] get(Event event) {
        Navigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return null;
        Vector hitbox = navigation.getHitbox();
        return hitbox != null ? new Vector[]{ hitbox } : null;
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ Vector.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || delta.length == 0) return;
        if (!(delta[0] instanceof Vector hitbox)) return;
        Navigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return;
        navigation.setHitbox(hitbox);
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Vector> getReturnType() { return Vector.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "hitbox of " + navigationExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(NavigationVector.class, Vector.class)
                .addPattern("hitbox of navigation %navigation%")
                .build()
        );
    }
}
