package skwhy.modules.LongNavigationElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.doc.RequiredPlugins;

import ch.njol.skript.classes.Changer.ChangeMode;
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
import skwhy.chunkpath.longnav.LongNavigation;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Name("[Long Navigation] Viewers")
@Description("Gets, adds, removes, or replaces the list of players who can see the (virtual) entity of a long " +
    "navigation. Only relevant for long navigations created from a numeric ID rather than a real entity.")
@Examples({
    "set {_longnav} to a new long navigation with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player",
    "",
    "set {_players::*} to viewers of long navigation {_longnav}",
    "add player to viewers of long navigation {_longnav}",
    "remove player from viewers of long navigation {_longnav}",
    "set viewers of long navigation {_longnav} to all players"
})
@Since("1.5.0")
@RequiredPlugins("PacketEvents")
public class LongNavigationPlayers extends SimpleExpression<Player> {

    private int matchedPattern;
    private Expression<LongNavigation> navigationExpr;
    private Expression<Player> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.matchedPattern = matchedPattern;
        if (matchedPattern == 0) {
            this.navigationExpr = (Expression<LongNavigation>) exprs[0];
        } else if (matchedPattern == 1 || matchedPattern == 2) {
            this.playerExpr = (Expression<Player>) exprs[0];
            this.navigationExpr = (Expression<LongNavigation>) exprs[1];
        } else {
            this.navigationExpr = (Expression<LongNavigation>) exprs[0];
            this.playerExpr = (Expression<Player>) exprs[1];
        }
        return true;
    }

    @Override
    protected @Nullable Player[] get(Event event) {
        if (matchedPattern != 0) return null;
        LongNavigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return null;
        List<Player> players = navigation.getPlayers();
        return players.toArray(new Player[0]);
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET || mode == ChangeMode.ADD || mode == ChangeMode.REMOVE) {
            return new Class<?>[]{ Player.class };
        }
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        LongNavigation navigation = navigationExpr.getSingle(event);
        if (navigation == null || delta == null || delta.length == 0) return;

        if (mode == ChangeMode.ADD) {
            Player player = playerExpr.getSingle(event);
            if (player != null) navigation.addPlayer(player);
            return;
        }
        if (mode == ChangeMode.REMOVE) {
            Player player = playerExpr.getSingle(event);
            if (player != null) navigation.removePlayer(player);
            return;
        }
        if (mode == ChangeMode.SET) {
            if (delta[0] instanceof Player[] players) {
                navigation.setPlayers(Arrays.asList(players));
            } else if (delta[0] instanceof Player player) {
                navigation.setPlayers(List.of(player));
            } else if (delta[0] instanceof List<?> list) {
                List<Player> players = list.stream()
                    .filter(obj -> obj instanceof Player)
                    .map(obj -> (Player) obj)
                    .collect(Collectors.toList());
                navigation.setPlayers(players);
            }
        }
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends Player> getReturnType() { return Player.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return switch (matchedPattern) {
            case 0 -> "viewers of " + navigationExpr.toString(event, debug);
            case 1 -> "add player " + playerExpr.toString(event, debug) + " to viewers of " + navigationExpr.toString(event, debug);
            case 2 -> "remove player " + playerExpr.toString(event, debug) + " from viewers of " + navigationExpr.toString(event, debug);
            default -> "set viewers of " + navigationExpr.toString(event, debug);
        };
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(LongNavigationPlayers.class, Player.class)
                .addPattern("viewers of long navigation %longnavigation%")
                .build()
        );
    }
}
