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

import java.util.ArrayList;
import java.util.List;

@Name("[Long Navigation] Viewers")
@Description("Gets, adds, removes, or replaces the list of players who can see the (virtual) entity of a long " +
    "navigation. Only relevant for long navigations created from a numeric ID rather than a real entity.")
@Examples({
    "set {_longnav} to a new long navigation with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player",
    "",
    "set {_players::*} to viewers of long navigation {_longnav}",
    "add player to viewers of long navigation {_longnav}",
    "remove player from viewers of long navigation {_longnav}",
    "set viewers of long navigation {_longnav} to all players",
    "delete viewers of long navigation {_longnav}"
})
@Since("1.5.0")
@RequiredPlugins("PacketEvents")
public class LongNavigationPlayers extends SimpleExpression<Player> {

    private Expression<LongNavigation> navigationExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.navigationExpr = (Expression<LongNavigation>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Player[] get(Event event) {
        LongNavigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return null;
        List<Player> players = navigation.getPlayers();
        return players.toArray(new Player[0]);
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case SET, ADD, REMOVE, REMOVE_ALL -> new Class<?>[]{ Player.class, Player[].class };
            case DELETE, RESET -> new Class<?>[0];
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        LongNavigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return;

        List<Player> players = new ArrayList<>();
        if (delta != null) {
            for (Object value : delta) {
                if (value instanceof Player player) players.add(player);
                else if (value instanceof Player[] array) {
                    for (Player player : array) if (player != null) players.add(player);
                }
            }
        }

        switch (mode) {
            case SET -> navigation.setPlayers(players);
            case ADD -> { for (Player player : players) navigation.addPlayer(player); }
            case REMOVE, REMOVE_ALL -> { for (Player player : players) navigation.removePlayer(player); }
            case DELETE, RESET -> navigation.setPlayers(new ArrayList<>());
            default -> { }
        }
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends Player> getReturnType() { return Player.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "viewers of " + navigationExpr.toString(event, debug);
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
