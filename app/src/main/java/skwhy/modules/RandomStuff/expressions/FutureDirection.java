package skwhy.modules.RandomStuff.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.FutureRotationTracker;

public class FutureDirection extends SimpleExpression<Float> {

    private Expression<Player> playerExpr;
    private boolean isYaw; // true si on demande le yaw, false pour le pitch

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        this.playerExpr = (Expression<Player>) exprs[0];
        // On regarde quel motif a été déclenché via l'index du pattern
        this.isYaw = (matchedPattern == 0);
        return true;
    }

    @Override
    protected @Nullable Float[] get(Event event) {
        Player player = playerExpr.getSingle(event);
        if (player == null) return null;

        // Récupère les deux valeurs sous forme de tableau [yaw, pitch]
        float[] predicted = FutureRotationTracker.getPredictedRotation(player);

        // Renvoie l'index correspondant à ce qui a été demandé en Skript
        return new Float[]{ isYaw ? predicted[0] : predicted[1] };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Float> getReturnType() { return Float.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "predicted " + (isYaw ? "yaw" : "pitch") + " of " + playerExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(FutureDirection.class, Float.class)
                .addPattern("[the] predicted yaw of %player%")     // Pattern 0
                .addPattern("[the] predicted pitch of %player%")   // Pattern 1
                .build()
        );
    }
}