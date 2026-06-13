package skwhy.modules.FakePathFindingElements.expressions;

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
import skwhy.data.FakePathFinding;

@Name("Fake Pathfinding Type")
@Description("Gets or sets the movement type of a fake pathfinding object as a string. " +
    "Valid values are WALK, FLY, and SWIM. Invalid values are silently ignored and the type remains unchanged.")
@Examples({
    "set {_fake} to a new fake pathfinding with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player type \"WALK\"",
    "",
    "# Read the current pathfinding type",
    "set {_type} to pathfinding type of {_fake}",
    "",
    "# Change to flying movement",
    "set pathfinding type of {_fake} to \"FLY\"",
    "",
    "# Change to swimming movement",
    "set pathfinding type of {_fake} to \"SWIM\""
})
@Since("1.2.0")
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
