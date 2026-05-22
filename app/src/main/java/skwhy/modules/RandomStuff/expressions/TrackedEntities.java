package skwhy.modules.RandomStuff.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import org.skriptlang.skript.addon.SkriptAddon;
import skwhy.TrackedBy;

import java.util.List;

/**
 * Expression pour obtenir les entités trackées par un joueur.
 *
 * Pattern : [all] [the] [entities] tracked by %player%
 * Retourne toutes les entités trackées (visibles) par ce joueur.
 */
public class TrackedEntities extends SimpleExpression<Entity> {

    private Expression<Player> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        this.playerExpr = (Expression<Player>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Entity[] get(Event event) {
        Player player = playerExpr.getSingle(event);
        if (player == null) return null;

        // Récupérer les entités trackées par le joueur
        List<Entity> trackedEntities = TrackedBy.getTrackedEntities(player);
        return trackedEntities.toArray(new Entity[0]);
    }

    @Override
    public boolean isSingle() {
        return false; // Retourne une liste
    }

    @Override
    public Class<? extends Entity> getReturnType() {
        return Entity.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "entities tracked by " + playerExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(TrackedEntities.class, Entity.class)
                .addPattern("[all] [the] [entities] tracked by %player%")
                .build()
        );
    }
}
