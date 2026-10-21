package skwhy.modules.QuestElements.expressions;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.npc.CompassService;

@Name("[Quest] Compass")
@Description("Whether the quest compass is shown to a player: a boss bar with the four directions and, for the quest he follows " +
    "(see 'selected quest of'), the destinations of the current step (❖ objective, ⮝ / ⮟ objective above / below, ◯ search zone; the bar turns " +
    "green inside a search zone). Setting it to true shows the compass, false hides it. The choice is saved and restored on join.")
@Examples({
    "set compass of player to true",
    "if compass of player is true:",
    "\tsend \"Boussole active\""
})
@Since("1.6.0")
public class ExprCompass extends SimpleExpression<Boolean> {

    private Expression<Player> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        playerExpr = (Expression<Player>) exprs[0];
        return true;
    }

    @Override
    protected Boolean @Nullable [] get(Event event) {
        Player player = playerExpr.getSingle(event);
        return player == null ? null : new Boolean[]{ CompassService.isEnabled(player.getUniqueId()) };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ Boolean.class };
        if (mode == ChangeMode.RESET) return new Class<?>[0];
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        Player player = playerExpr.getSingle(event);
        if (player == null) return;
        boolean enabled = mode == ChangeMode.SET && delta != null && delta.length > 0 && delta[0] instanceof Boolean b && b;
        CompassService.setEnabled(player, enabled);
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Boolean> getReturnType() { return Boolean.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "compass of " + playerExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprCompass.class, Boolean.class)
                .addPattern("compass of %player%")
                .build()
        );
    }
}
