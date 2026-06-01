package skwhy.modules.FakePathFindingElements;

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
import skwhy.data.FakePathFinding;

public class FakePathFindingVector extends SimpleExpression<Vector> {

    private Expression<FakePathFinding> fakeExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.fakeExpr = (Expression<FakePathFinding>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Vector[] get(Event event) {
        FakePathFinding fake = fakeExpr.getSingle(event);
        if (fake == null) return null;
        Vector hitbox = fake.getHitbox();
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
        FakePathFinding fake = fakeExpr.getSingle(event);
        if (fake == null) return;
        fake.setHitbox(hitbox);
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Vector> getReturnType() { return Vector.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "hitbox of " + fakeExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(FakePathFindingVector.class, Vector.class)
                .addPattern("hitbox of %fakepathfinding%")
                .build()
        );
    }
}
