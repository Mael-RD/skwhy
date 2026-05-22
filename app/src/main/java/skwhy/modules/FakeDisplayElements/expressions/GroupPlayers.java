package skwhy.modules.FakeDisplayElements.expressions;

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
    public Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case ADD, REMOVE, REMOVE_ALL -> new Class<?>[]{Player[].class};
            default          -> null;
        };
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