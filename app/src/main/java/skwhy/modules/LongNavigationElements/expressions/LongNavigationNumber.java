package skwhy.modules.LongNavigationElements.expressions;

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
import skwhy.chunkpath.longnav.LongNavigation;

@Name("[Long Navigation] Numeric Properties")
@Description("Gets or sets numeric properties of a long navigation object: movement speed, pause duration in " +
    "ticks, or the numeric entity ID (read-only).")
@Examples({
    "set {_longnav} to a new long navigation with entity target entity speed 0.2",
    "",
    "set {_speed} to speed of long navigation {_longnav}",
    "set speed of long navigation {_longnav} to 0.3",
    "",
    "set pause ticks of long navigation {_longnav} to 0",
    "",
    "set {_id} to entity id of long navigation {_longnav}"
})
@Since("1.5.0")
public class LongNavigationNumber extends SimpleExpression<Number> {

    private int matchedPattern;
    private Expression<LongNavigation> navigationExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.matchedPattern = matchedPattern;
        this.navigationExpr = (Expression<LongNavigation>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Number[] get(Event event) {
        LongNavigation navigation = navigationExpr.getSingle(event);
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
        LongNavigation navigation = navigationExpr.getSingle(event);
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
            default -> "long navigation number";
        };
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(LongNavigationNumber.class, Number.class)
                .addPattern("speed of long navigation %longnavigation%")
                .addPattern("pause ticks of long navigation %longnavigation%")
                .addPattern("entity id of long navigation %longnavigation%")
                .build()
        );
    }
}
