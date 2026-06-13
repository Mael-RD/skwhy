package skwhy.modules.FakePathFindingElements.expressions;

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
import skwhy.data.FakePathFinding;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Name("Fake Pathfinding Viewers")
@Description("Gets, adds, removes, or replaces the list of players who can see a fake pathfinding entity. " +
    "Supports SET, ADD, and REMOVE change modes via dedicated syntax patterns.")
@Examples({
    "set {_fake} to a new fake pathfinding with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player type \"WALK\"",
    "",
    "# Read the current viewer list",
    "set {_players::*} to players of {_fake}",
    "",
    "# Add a player to the viewer list",
    "add player player to players of {_fake}",
    "",
    "# Remove a player from the viewer list",
    "remove player player from players of {_fake}",
    "",
    "# Replace the entire viewer list",
    "set players of {_fake} to all players"
})
@Since("1.2.0")
@RequiredPlugins("PacketEvents")
public class FakePathFindingPlayers extends SimpleExpression<Player> {

    private int matchedPattern;
    private Expression<FakePathFinding> fakeExpr;
    private Expression<Player> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.matchedPattern = matchedPattern;
        if (matchedPattern == 0) {
            this.fakeExpr = (Expression<FakePathFinding>) exprs[0];
        } else if (matchedPattern == 1 || matchedPattern == 2) {
            this.playerExpr = (Expression<Player>) exprs[0];
            this.fakeExpr = (Expression<FakePathFinding>) exprs[1];
        } else {
            this.fakeExpr = (Expression<FakePathFinding>) exprs[0];
            this.playerExpr = (Expression<Player>) exprs[1];
        }
        return true;
    }

    @Override
    protected @Nullable Player[] get(Event event) {
        if (matchedPattern != 0) return null;
        FakePathFinding fake = fakeExpr.getSingle(event);
        if (fake == null) return null;
        List<Player> players = fake.getPlayers();
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
        FakePathFinding fake = fakeExpr.getSingle(event);
        if (fake == null || delta == null || delta.length == 0) return;

        if (mode == ChangeMode.ADD) {
            Player player = playerExpr.getSingle(event);
            if (player != null) fake.addPlayer(player);
            return;
        }
        if (mode == ChangeMode.REMOVE) {
            Player player = playerExpr.getSingle(event);
            if (player != null) fake.removePlayer(player);
            return;
        }
        if (mode == ChangeMode.SET) {
            if (delta[0] instanceof Player[] players) {
                fake.setPlayers(Arrays.asList(players));
            } else if (delta[0] instanceof Player player) {
                fake.setPlayers(List.of(player));
            } else if (delta[0] instanceof List<?> list) {
                List<Player> players = list.stream()
                    .filter(obj -> obj instanceof Player)
                    .map(obj -> (Player) obj)
                    .collect(Collectors.toList());
                fake.setPlayers(players);
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
            case 0 -> "players of " + fakeExpr.toString(event, debug);
            case 1 -> "add player " + playerExpr.toString(event, debug) + " to players of " + fakeExpr.toString(event, debug);
            case 2 -> "remove player " + playerExpr.toString(event, debug) + " from players of " + fakeExpr.toString(event, debug);
            default -> "set players of " + fakeExpr.toString(event, debug);
        };
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(FakePathFindingPlayers.class, Player.class)
                .addPattern("path viewers of %fakepathfinding%")
                .build()
        );
    }
}
