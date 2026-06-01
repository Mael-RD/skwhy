package skwhy.modules.FakePathFindingElements;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.FakePathFinding;

public class FakePathFindingLocation extends SimpleExpression<Location> {

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
    protected @Nullable Location[] get(Event event) {
        FakePathFinding fake = fakeExpr.getSingle(event);
        if (fake == null) return null;
        Location value = (matchedPattern == 0) ? fake.getLocation() : fake.getDestination();
        return value != null ? new Location[]{ value } : null;
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ Location.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || delta.length == 0) return;
        if (!(delta[0] instanceof Location location)) return;
        FakePathFinding fake = fakeExpr.getSingle(event);
        if (fake == null) return;

        if (matchedPattern == 0) {
            fake.setLocation(location);
        } else {
            fake.setDestination(location);
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Location> getReturnType() { return Location.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return switch (matchedPattern) {
            case 0 -> "location of " + fakeExpr.toString(event, debug);
            default -> "destination of " + fakeExpr.toString(event, debug);
        };
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(FakePathFindingLocation.class, Location.class)
                .addPattern("location of %fakepathfinding%")
                .addPattern("destination of %fakepathfinding%")
                .build()
        );
    }
}
