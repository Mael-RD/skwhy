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

public class GroupPlayers extends SimpleExpression<Player> {

    private Expression<DisplayGroupData> groupExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        groupExpr = (Expression<DisplayGroupData>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Player[] get(Event event) {
        DisplayGroupData group = groupExpr.getSingle(event);
        if (group == null) return null;
        return group.getViewers().toArray(new Player[0]);
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case ADD, REMOVE -> new Class<?>[]{ Player.class, Player[].class };
            case REMOVE_ALL  -> new Class<?>[0];
            default          -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        DisplayGroupData group = groupExpr.getSingle(event);
        if (group == null) return;

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
            case REMOVE_ALL -> group.clearViewers();
            default -> { }
        }
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends Player> getReturnType() { return Player.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "players of " + groupExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(GroupPlayers.class, Player.class)
                .addPattern("[the] players of %displaygroup%")
                .build()
        );
    }
}