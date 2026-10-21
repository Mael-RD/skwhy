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
import skwhy.quest.QuestEngine;
import skwhy.quest.model.Quest;

@Name("[Quest] Selected Quest")
@Description("The quest a player follows: its destinations are shown on the compass. Can be set to a quest or deleted.")
@Examples({
    "set selected quest of player to quest \"chasseur\"",
    "send \"Suivie : %quest name of selected quest of player%\"",
    "delete selected quest of player"
})
@Since("1.6.0")
public class ExprSelectedQuest extends SimpleExpression<Quest> {

    private Expression<Player> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        playerExpr = (Expression<Player>) exprs[0];
        return true;
    }

    @Override
    protected Quest @Nullable [] get(Event event) {
        Player player = playerExpr.getSingle(event);
        if (player == null) return null;
        Quest quest = QuestEngine.selected(player);
        return quest == null ? null : new Quest[]{ quest };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ Quest.class };
        if (mode == ChangeMode.DELETE || mode == ChangeMode.RESET) return new Class<?>[0];
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        Player player = playerExpr.getSingle(event);
        if (player == null) return;
        if (mode == ChangeMode.SET && delta != null && delta.length > 0 && delta[0] instanceof Quest quest) {
            QuestEngine.select(player, quest);
        } else if (mode != ChangeMode.SET) {
            QuestEngine.select(player, null);
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Quest> getReturnType() { return Quest.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "selected quest of " + playerExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprSelectedQuest.class, Quest.class)
                .addPattern("selected quest of %player%")
                .build()
        );
    }
}
