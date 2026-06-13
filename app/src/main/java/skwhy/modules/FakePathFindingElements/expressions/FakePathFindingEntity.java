package skwhy.modules.FakePathFindingElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.FakePathFinding;

@Name("Fake Pathfinding Entity")
@Description("Returns the real Bukkit entity attached to a fake pathfinding object. " +
    "Returns nothing if the pathfinding was created from a numeric ID rather than a real entity.")
@Examples({
    "set {_fake} to a new fake pathfinding with entity target entity hitbox vector(0.6, 1.8, 0.6) location location of player type \"WALK\"",
    "",
    "# Get the entity using the 'entity of' syntax",
    "set {_entity} to entity of {_fake}",
    "",
    "# Get the entity using the possessive syntax",
    "set {_entity} to {_fake}'s entity"
})
@Since("1.2.0")
public class FakePathFindingEntity extends SimpleExpression<Entity> {

    private Expression<FakePathFinding> fakeExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.fakeExpr = (Expression<FakePathFinding>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Entity[] get(Event event) {
        FakePathFinding fake = fakeExpr.getSingle(event);
        if (fake == null || !fake.isRealEntity()) return null;
        Entity entity = fake.getEntity();
        if (entity == null) return null;
        return new Entity[]{ entity };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Entity> getReturnType() { return Entity.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "entity of " + fakeExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(FakePathFindingEntity.class, Entity.class)
                .addPattern("entity of %fakepathfinding%")
                .addPattern("%fakepathfinding%'s entity")
                .build()
        );
    }
}