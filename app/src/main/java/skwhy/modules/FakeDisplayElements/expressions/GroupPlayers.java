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
@Description("Gets, sets, adds, or removes the players who can see a display group or a cosmetic. Supports SET, ADD, REMOVE, and REMOVE_ALL change modes.")
@Examples({
    "set {_group} to a new fake display group at player",
    "set {_cosmetique} to a new cosmetique for player",
    "",
    "# Add a viewer to a group",
    "add player to viewers of {_group}",
    "",
    "# Remove a viewer from a cosmetic",
    "remove player from viewers of {_cosmetique}",
    "",
    "# Replace all viewers of a group with a single player",
    "set viewers of {_group} to player",
    "",
    "# Clear all viewers",
    "remove all from viewers of {_cosmetique}"
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
        // On indique à Skript que notre expression accepte d'être modifiée (SET, ADD, REMOVE, REMOVE_ALL)
        // et que l'objet qu'on va lui passer (le delta) doit être un Player
        if (mode == ChangeMode.SET || mode == ChangeMode.ADD || mode == ChangeMode.REMOVE || mode == ChangeMode.REMOVE_ALL) {
            return new Class<?>[] { Player.class }; // C'est cette ligne qui débloque le "set viewers of ... to player"
        }
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        Object target = targetExpr.getSingle(event);
        if (target == null) return;

        // Cas 1 : Gestion directe pour DisplayGroupData
        if (target instanceof DisplayGroupData group) {
            switch (mode) {
                case ADD -> {
                    if (delta == null) return;
                    for (Object obj : delta) {
                        if (obj instanceof Player player) group.addViewer(player);
                    }
                }
                case REMOVE -> {
                    if (delta == null) return;
                    for (Object obj : delta) {
                        if (obj instanceof Player player) group.removeViewer(player);
                    }
                }
                case SET -> {
                    if (delta == null) return;
                    List<Player> newViewers = new ArrayList<>();
                    for (Object obj : delta) {
                        if (obj instanceof Player player) newViewers.add(player);
                    }
                    group.setViewers(newViewers);
                }
                case REMOVE_ALL -> group.clearViewers();
                default -> { }
            }
        } 
        // Cas 2 : Gestion directe pour ton Cosmétique
        else if (target instanceof CosmetiqueData cosme) {
            switch (mode) {
                case ADD -> {
                    if (delta == null) return;
                    for (Object obj : delta) {
                        if (obj instanceof Player player) cosme.addViewer(player);
                    }
                }
                case REMOVE -> {
                    if (delta == null) return;
                    for (Object obj : delta) {
                        if (obj instanceof Player player) cosme.removeViewer(player);
                    }
                }
                case SET -> {
                    if (delta == null) return;
                    List<Player> newViewers = new ArrayList<>();
                    for (Object obj : delta) {
                        if (obj instanceof Player player) newViewers.add(player);
                    }
                    cosme.setViewers(newViewers);
                }
                case REMOVE_ALL -> cosme.clearViewers();
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
                .addPattern("[the] viewers of %displaygroups/cosmetiques%")
                .build()
        );
    }
}