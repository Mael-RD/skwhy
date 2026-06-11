package skwhy.modules.FakePathFindingElements.expressions;

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
import skwhy.data.FakePathFinding;

public class FakePathFindingNumber extends SimpleExpression<Number> {

    private int matchedPattern;
    private Expression<FakePathFinding> fakeExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.matchedPattern = matchedPattern;
        this.fakeExpr = (Expression<FakePathFinding>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Number[] get(Event event) {
        FakePathFinding fake = fakeExpr.getSingle(event);
        if (fake == null) return null;

        Number value = switch (matchedPattern) {
            case 0 -> fake.getSpeed();
            case 1 -> fake.getPauseTicks();
            case 2 -> fake.getEntityId();
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
        FakePathFinding fake = fakeExpr.getSingle(event);
        if (fake == null) return;

        switch (matchedPattern) {
            case 0 -> fake.setSpeed(n.doubleValue());
            case 1 -> fake.setPauseTicks(n.intValue());
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
            case 0 -> "speed of " + fakeExpr.toString(event, debug);
            case 1 -> "pause ticks of " + fakeExpr.toString(event, debug);
            case 2 -> "entity id of " + fakeExpr.toString(event, debug);
            default -> "fake pathfinding number";
        };
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(FakePathFindingNumber.class, Number.class)
                .addPattern("speed of %fakepathfinding%")
                .addPattern("pause ticks of %fakepathfinding%")
                .addPattern("entity id of %fakepathfinding%")
                .build()
        );
    }
}
