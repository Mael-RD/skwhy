package skwhy.modules.RandomStuff.expressions;


import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Since;

import ch.njol.skript.classes.Changer.ChangeMode;
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

import skwhy.BodyTracker;

@Name("Body Yaw")
@Description("Gets or sets the body yaw rotation of an entity.")
@Examples({
    "set the body yaw of target to 90",
    "broadcast \"Your body yaw is %body yaw of player%\""
})
@Since("1.0.0")
public class BodyYaw extends SimpleExpression<Float> {

    private Expression<Player> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        this.playerExpr = (Expression<Player>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Float[] get(Event event) {
        Player player = playerExpr.getSingle(event);
        if (player == null) return null;

        // Appel de ton algorithme personnalisé via la classe statique !
        float customBodyYaw = BodyTracker.getCustomBodyYaw(player);
        
        return new Float[]{ customBodyYaw };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        // Optionnel : si l'API Paper permet de modifier le bodyYaw, tu pourrais l'ajouter ici.
        // Sinon, on retourne null pour dire que c'est une valeur en lecture seule.
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        // Laisser vide si on ne gère pas le 'set'
    }

    @Override
    public boolean isSingle() { 
        return true; 
    }

    @Override
    public Class<? extends Float> getReturnType() { 
        return Float.class; 
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "body yaw of " + playerExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(BodyYaw.class, Float.class)
                .addPattern("[the] body yaw of %player%")
                .addPattern("%player%'s body yaw")
                .build()
        );
    }
}