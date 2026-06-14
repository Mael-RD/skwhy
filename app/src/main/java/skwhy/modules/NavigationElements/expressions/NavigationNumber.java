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
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.pathfinder.Navigation;

@Name("[Navigation] Numeric Properties")
@Description("Gets or sets numeric properties of a navigation object: movement speed, pause duration in ticks, or the numeric entity ID (read-only).")
@Examples({
    "set {_navigation} to a new fake navigation with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player type \"WALK\"",
    "",
    "# Read and update movement speed",
    "set {_speed} to speed of navigation {_navigation}",
    "set speed of navigation {_navigation} to 0.3",
    "",
    "# Read and update the pause duration",
    "set {_pause} to pause ticks of navigation {_navigation}",
    "set pause ticks of navigation {_navigation} to 20",
    "",
    "# Read the numeric entity ID (read-only)",
    "set {_id} to entity id of navigation {_navigation}"
})
@Since("1.2.0")
public class NavigationNumber extends SimpleExpression<Number> {

    private int matchedPattern;
    private Expression<Navigation> navigationExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.matchedPattern = matchedPattern;
        this.navigationExpr = (Expression<Navigation>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Number[] get(Event event) {
        Navigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return null;

        Number value = switch (matchedPattern) {
            case 0 -> navigation.getSpeed();
            case 1 -> navigation.getPauseTicks();
            case 2 -> navigation.getEntityId();
            default -> null;
        };
        return value != null ? new Number[]{ value } : null;
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ Number.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || delta.length == 0) return;
        if (!(delta[0] instanceof Number n)) return;
        Navigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return;

        switch (matchedPattern) {
            case 0 -> navigation.setSpeed(n.floatValue());
            case 1 -> navigation.setPauseTicks(n.intValue());
            default -> {
            }
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Number> getReturnType() { return Number.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return switch (matchedPattern) {
            case 0 -> "speed of " + navigationExpr.toString(event, debug);
            case 1 -> "pause ticks of " + navigationExpr.toString(event, debug);
            case 2 -> "entity id of " + navigationExpr.toString(event, debug);
            default -> "fake pathfinding number";
        };
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(NavigationNumber.class, Number.class)
                .addPattern("[fake] speed of navigation %navigation%")
                .addPattern("[fake] pause ticks of navigation %navigation%")
                .addPattern("[fake] entity id of navigation %navigation%")
                .build()
        );
    }
}
