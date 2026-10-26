package skwhy.modules.QuestElements.expressions;

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
import skwhy.quest.model.QuestObjective;
import skwhy.quest.model.QuestStep;

@Name("[Quest] Current Objectives")
@Description("The objectives of the step a player is currently on in a quest, in order. Nothing is returned when the player " +
    "has not started the quest or has finished it.")
@Examples({
    "loop current objectives of quest \"chasseur\" for player:",
    "\tsend \"%quest objective text of loop-value%\""
})
@Since("1.6.0")
public class ExprCurrentObjectives extends SimpleExpression<QuestObjective> {

    private Expression<Quest> questExpr;
    private Expression<OfflinePlayer> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        questExpr = (Expression<Quest>) exprs[0];
        playerExpr = (Expression<OfflinePlayer>) exprs[1];
        return true;
    }

    @Override
    protected QuestObjective @Nullable [] get(Event event) {
        Quest quest = questExpr.getSingle(event);
        OfflinePlayer player = playerExpr.getSingle(event);
        if (quest == null || player == null) return null;
        QuestStep step = quest.step(QuestEngine.step(player, quest));
        return step == null ? new QuestObjective[0] : step.objectives().toArray(new QuestObjective[0]);
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends QuestObjective> getReturnType() { return QuestObjective.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "current objectives of " + questExpr.toString(event, debug) + " for " + playerExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprCurrentObjectives.class, QuestObjective.class)
                .addPattern("current objectives of %quest% for %offlineplayer%")
                .build()
        );
    }
}
