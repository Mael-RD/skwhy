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
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.QuestEngine;
import skwhy.quest.model.Quest;

@Name("[Quest] Quest Objective Progress")
@Description("How far a player is in an objective of the step he is currently on, from 0 to the total the objective asks (objectives are numbered from 1). " +
    "Set, add and remove change it; reaching the total completes the objective, and completing all the objectives of the step moves to the next step. " +
    "Nothing is changed when the player does not have this quest or the objective does not exist.")
@Examples({
    "send \"%quest progress of objective 1 of quest \"chasseur\" for player%\"",
    "add 5 to quest progress of objective 2 of quest \"chasseur\" for player",
    "set quest progress of objective 1 of quest \"chasseur\" for player to 0"
})
@Since("1.6.0")
public class ExprObjectiveProgress extends SimpleExpression<Integer> {

    private Expression<Number> objectiveExpr;
    private Expression<Quest> questExpr;
    private Expression<OfflinePlayer> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        objectiveExpr = (Expression<Number>) exprs[0];
        questExpr = (Expression<Quest>) exprs[1];
        playerExpr = (Expression<OfflinePlayer>) exprs[2];
        return true;
    }

    @Override
    protected Integer @Nullable [] get(Event event) {
        Number objective = objectiveExpr.getSingle(event);
        Quest quest = questExpr.getSingle(event);
        OfflinePlayer player = playerExpr.getSingle(event);
        if (objective == null || quest == null || player == null) return null;
        return new Integer[]{ QuestEngine.progress(player, quest, objective.intValue() - 1) };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case SET, ADD, REMOVE -> new Class<?>[]{ Number.class };
            case RESET -> new Class<?>[0];
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        Number objective = objectiveExpr.getSingle(event);
        Quest quest = questExpr.getSingle(event);
        OfflinePlayer player = playerExpr.getSingle(event);
        if (objective == null || quest == null || player == null) return;
        int step = QuestEngine.step(player, quest);
        if (step < 0) return;
        int index = objective.intValue() - 1;
        if (mode == ChangeMode.RESET) {
            QuestEngine.progress(player, quest, step, index, 0, QuestEngine.Op.SET);
            return;
        }
        if (delta == null || delta.length == 0 || !(delta[0] instanceof Number number)) return;
        QuestEngine.Op op = mode == ChangeMode.ADD ? QuestEngine.Op.ADD : mode == ChangeMode.REMOVE ? QuestEngine.Op.REMOVE : QuestEngine.Op.SET;
        QuestEngine.progress(player, quest, step, index, number.intValue(), op);
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Integer> getReturnType() { return Integer.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "quest progress of objective " + objectiveExpr.toString(event, debug) + " of " + questExpr.toString(event, debug)
            + " for " + playerExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprObjectiveProgress.class, Integer.class)
                .addPattern("quest progress of objective %number% of %quest% for %offlineplayer%")
                .build()
        );
    }
}
