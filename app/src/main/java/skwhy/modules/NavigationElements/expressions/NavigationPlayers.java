package skwhy.modules.NavigationElements.expressions;

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
import skwhy.pathfinder.Navigation;

import java.util.ArrayList;
import java.util.List;

@Name("[Navigation] Viewers")
@Description("Gets, adds, removes, or replaces the list of players who can see the (fake) entity fake navigating. " +
    "Supports set, add, remove and delete/reset (clears the viewers) with one player or a list of players.")
@Examples({
    "set {_fake} to a new fake navigation with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player type \"WALK\"",
    "",
    "# Read the current viewer list",
    "set {_players::*} to viewers of navigation {_fake}",
    "",
    "# Add a player to the viewer list",
    "add player to viewers of navigation {_fake}",
    "",
    "# Remove a player from the viewer list",
    "remove player from viewers of navigation {_fake}",
    "",
    "# Replace the entire viewer list",
    "set viewers of navigation {_fake} to all players",
    "",
    "# Remove every viewer",
    "delete viewers of navigation {_fake}"
})
@Since("1.2.0")
@RequiredPlugins("PacketEvents")
public class NavigationPlayers extends SimpleExpression<Player> {

    private Expression<Navigation> fakeExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.fakeExpr = (Expression<Navigation>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Player[] get(Event event) {
        Navigation fake = fakeExpr.getSingle(event);
        if (fake == null) return null;
        List<Player> players = fake.getPlayers();
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
        Navigation fake = fakeExpr.getSingle(event);
        if (fake == null) return;

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
            case SET -> fake.setPlayers(players);
            case ADD -> { for (Player player : players) fake.addPlayer(player); }
            case REMOVE, REMOVE_ALL -> { for (Player player : players) fake.removePlayer(player); }
            case DELETE, RESET -> fake.setPlayers(new ArrayList<>());
            default -> { }
        }
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends Player> getReturnType() { return Player.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "viewers of " + fakeExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(NavigationPlayers.class, Player.class)
                .addPattern("[fake] viewers of navigation %navigation%")
                .build()
        );
    }
}
