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
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import org.skriptlang.skript.addon.SkriptAddon;

import java.util.Collection;

/**
 * Expression pour obtenir tous les joueurs qui "trackent" une entité.
 * Note : Nécessite Paper (ou une version de Bukkit récente supportant getTrackers).
 */
@Name("Entity Trackers")
@Description("Gets a list of all players who are currently tracking (seeing) a specific entity.")
@Examples({
    "set {_trackers::*} to trackers of target entity",
    "broadcast \"This entity is seen by %size of {_trackers::*}% players.\""
})
@Since("1.0.0")
public class EntityTrackers extends SimpleExpression<Player> {

    private Expression<Entity> entityExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        this.entityExpr = (Expression<Entity>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Player[] get(Event event) {
        Entity entity = entityExpr.getSingle(event);
        if (entity == null) return null;

        // .getTrackers() renvoie une collection de joueurs qui voient l'entité
        // et suivent ses mises à jour réseau.
        Collection<Player> trackers = entity.getTrackedBy();
        
        return trackers.toArray(new Player[0]);
    }

    @Override
    public boolean isSingle() {
        return false; // Retourne une liste de joueurs
    }

    @Override
    public Class<? extends Player> getReturnType() {
        return Player.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "trackers of " + entityExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(EntityTrackers.class, Player.class)
                .addPattern("[all] [the] trackers of %entity%")
                .build()
        );
    }
}