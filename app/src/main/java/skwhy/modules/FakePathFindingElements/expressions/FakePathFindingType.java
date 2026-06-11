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

public class FakePathFindingType extends SimpleExpression<String> {

    private Expression<FakePathFinding> fakeExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.fakeExpr = (Expression<FakePathFinding>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable String[] get(Event event) {
        FakePathFinding fake = fakeExpr.getSingle(event);
        if (fake == null || fake.getPathfindingType() == null) return null;
        return new String[]{ fake.getPathfindingType().name() };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ String.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || delta.length == 0) return;
        FakePathFinding fake = fakeExpr.getSingle(event);
        if (fake == null) return;

        Object raw = delta[0];
        if (raw instanceof String stringValue) {
            try {
                fake.setPathfindingType(FakePathFinding.PathfindingType.valueOf(stringValue.trim().toUpperCase().replace(' ', '_')));
            } catch (IllegalArgumentException ignored) {
            }
        } else if (raw instanceof FakePathFinding.PathfindingType type) {
            fake.setPathfindingType(type);
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "pathfinding type of " + fakeExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(FakePathFindingType.class, String.class)
                .addPattern("pathfinding type of %fakepathfinding%")
                .build()
        );
    }
}
