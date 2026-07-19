package skwhy.modules.RandomStuff.expressions;

import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Since;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import org.skriptlang.skript.addon.SkriptAddon;

/**
 * Expression pour obtenir l'ID d'une entité spécifique.
 *
 * Pattern : entity id of %entity%
 * Retourne l'ID de l'entité spécifiée.
 */
@Name("Entity ID")
@Description("Gets the ID of a specific entity.")
@Examples({
    "set {_id} to entity ID of target",
})
@Since("1.2.0")
public class EntityId extends SimpleExpression<Integer> {

    private Expression<Entity> entityExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        this.entityExpr = (Expression<Entity>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Integer[] get(Event event) {
        Entity entity = entityExpr.getSingle(event);
        if (entity == null) return null;

        return new Integer[]{entity.getEntityId()};
    }

    @Override
    public boolean isSingle() {
        return true; // Retourne un seul ID
    }

    @Override
    public Class<? extends Integer> getReturnType() {
        return Integer.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "entity ID of " + entityExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(EntityId.class, Integer.class)
                .addPattern("entity id of %entity%")
                .build()
        );
    }
}
