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

@Name("[Long Navigation] Cost Smoothing")
@Description("Size in blocks under which a patch of blocks that costs more than its surroundings is ignored by the walk " +
    "of a long navigation (4 by default). The path cost rules describe the large scale (a road, a field, a plaza); " +
    "at a small scale the entity prefers Minecraft's own movement costs, so it walks straight over a grass block in the " +
    "middle of a dirt path instead of going around it. A wide area (a road, a field) keeps its cost. " +
    "The value is rounded to a whole number of blocks; 0 applies the cost rules block by block. " +
    "Resetting gives back the default. Only the walk between the chunk gates is concerned: the route through the " +
    "chunks is still decided with the cost rules alone.")
@Examples({
    "set {_longnav} to a new long navigation with entity target entity speed 0.2 using rules {_rules::*}",
    "set cost smoothing of long navigation {_longnav} to 5",
    "set cost smoothing of long navigation {_longnav} to 0",
    "reset cost smoothing of long navigation {_longnav}"
})
@Since("1.6.0")
public class LongNavigationCostSmoothing extends SimpleExpression<Number> {

    private static final int MAX_BLOCKS = 16;

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
        return new Number[]{ navigation.getCostSmoothing() };
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
            navigation.setCostSmoothing(-1);
        } else if (mode == ChangeMode.SET && delta != null && delta.length > 0 && delta[0] instanceof Number n) {
            int blocks = (int) Math.round(n.doubleValue());
            navigation.setCostSmoothing(Math.max(0, Math.min(MAX_BLOCKS, blocks)));
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Number> getReturnType() { return Number.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "cost smoothing of " + navigationExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(LongNavigationCostSmoothing.class, Number.class)
                .addPattern("cost smoothing of long navigation %longnavigation%")
                .build()
        );
    }
}
