package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.doc.RequiredPlugins;

import ch.njol.skript.classes.Changer.ChangeMode;
import org.bukkit.entity.Player;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.data.DisplayGroupData;
import skwhy.data.CosmetiqueData;

import java.util.List;
import java.util.ArrayList;

@Name("Group Viewers")
@Description("Gets, sets, adds, or removes the players who can see a display group or a cosmetic. Supports set, add, remove, remove all and delete/reset (clears the viewers).")
@Examples({
    "set {_group} to a new fake display group at player",
    "set {_cosmetique} to a new cosmetique for player",
    "",
    "# Add a viewer to a group",
    "add player to group viewers of {_group}",
    "add {-players::*} to group viewers of {_group}",
    "",
    "# Remove a viewer from a cosmetic",
    "remove player from group viewers of {_cosmetique}",
    "",
    "# Replace all viewers of a group with a single player",
    "set group viewers of {_group} to player",
    "set group viewers of {_group} to {-players::*}",
    "",
    "# Clear all viewers",
    "delete group viewers of {_cosmetique}"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class GroupPlayers extends SimpleExpression<Player> {

    private Expression<Object> targetExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        targetExpr = (Expression<Object>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Player[] get(Event event) {
        Object target = targetExpr.getSingle(event);
        if (target == null) return null;

        // Cast direct selon le type de l'objet (uniquement les 2 types possibles)
        if (target instanceof DisplayGroupData group) {
            return group.getViewers().toArray(new Player[0]);
        } else if (target instanceof CosmetiqueData cosme) {
            return cosme.getViewers().toArray(new Player[0]);
        }

        return null;
    }
    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case SET, ADD, REMOVE, REMOVE_ALL -> new Class<?>[]{Player.class, Player[].class};
            case DELETE, RESET -> new Class<?>[0];
            default -> null;
        };
    }

    private List<Player> playersFromDelta(Object @Nullable [] delta) {
        List<Player> players = new ArrayList<>();
        if (delta == null) return players;

        for (Object value : delta) {
            if (value instanceof Player player) {
                players.add(player);
            } else if (value instanceof Player[] playerArray) {
                for (Player player : playerArray) {
                    if (player != null) players.add(player);
                }
            }
        }
        return players;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        Object target = targetExpr.getSingle(event);
        if (target == null) return;

        // Cas 1 : Gestion directe pour DisplayGroupData
        if (target instanceof DisplayGroupData group) {
            List<Player> changedPlayers = playersFromDelta(delta);
            switch (mode) {
                case ADD -> {
                    for (Player player : changedPlayers) group.addViewer(player);
                }
                case REMOVE, REMOVE_ALL -> {
                    for (Player player : changedPlayers) group.removeViewer(player);
                }
                case SET -> group.setViewers(changedPlayers);
                case DELETE, RESET -> group.clearViewers();
                default -> { }
            }
        } 
        // Cas 2 : Gestion directe pour ton Cosmétique
        else if (target instanceof CosmetiqueData cosme) {
            List<Player> changedPlayers = playersFromDelta(delta);
            switch (mode) {
                case ADD -> {
                    for (Player player : changedPlayers) cosme.addViewer(player);
                }
                case REMOVE, REMOVE_ALL -> {
                    for (Player player : changedPlayers) cosme.removeViewer(player);
                }
                case SET -> cosme.setViewers(changedPlayers);
                case DELETE, RESET -> cosme.clearViewers();
                default -> { }
            }
        }
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends Player> getReturnType() { return Player.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "players of " + targetExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(GroupPlayers.class, Player.class)
                // Le pattern accepte maintenant les displaygroups ou les cosmetiques enregistrés dans Skript
                .addPattern("[the] group viewers of %displaygroups/cosmetiques%")
                .build()
        );
    }
}