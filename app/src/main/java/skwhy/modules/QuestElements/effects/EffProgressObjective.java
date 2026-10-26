package skwhy.modules.QuestElements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.QuestEngine;
import skwhy.quest.model.Quest;

@Name("[Quest] Progress / Complete Objective")
@Description("Makes players progress in an objective of the step they are currently on (objectives are numbered from 1): 'progress' adds an amount (1 by default), " +
    "'complete' fills the objective. When all the objectives of the step are complete, the players move on to the next step. " +
    "Players that do not have the quest, or whose current step has no such objective, are ignored.")
@Examples({
    "progress objective 1 of quest \"chasseur\" for player",
    "progress objective 2 of quest \"chasseur\" for player by 5",
    "complete objective 1 of quest \"main\" for all players"
})
@Since("1.6.0")
public class EffProgressObjective extends Effect {

    private Expression<Number> objectiveExpr;
    private Expression<Quest> questExpr;
    private Expression<OfflinePlayer> playersExpr;
    private @Nullable Expression<Number> amountExpr;
    private boolean complete;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffProgressObjective.class)
                .addPattern("progress objective %number% of %quest% for %offlineplayers% [by %-number%]")
                .addPattern("complete objective %number% of %quest% for %offlineplayers%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        objectiveExpr = (Expression<Number>) exprs[0];
        questExpr = (Expression<Quest>) exprs[1];
        playersExpr = (Expression<OfflinePlayer>) exprs[2];
        complete = matchedPattern == 1;
        amountExpr = complete ? null : (Expression<Number>) exprs[3];
        return true;
    }

    @Override
    protected void execute(Event event) {
        Number objective = objectiveExpr.getSingle(event);
        Quest quest = questExpr.getSingle(event);
        if (objective == null || quest == null) return;
        int index = objective.intValue() - 1;
        Number amount = amountExpr == null ? null : amountExpr.getSingle(event);
        for (OfflinePlayer player : playersExpr.getArray(event)) {
            if (complete) {
                QuestEngine.complete(player, quest, index);
            } else {
                int step = QuestEngine.step(player, quest);
                if (step >= 0) QuestEngine.progress(player, quest, step, index, amount == null ? 1 : amount.intValue(), QuestEngine.Op.ADD);
            }
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return (complete ? "complete" : "progress") + " objective " + objectiveExpr.toString(event, debug) + " of " + questExpr.toString(event, debug)
            + " for " + playersExpr.toString(event, debug) + (amountExpr == null ? "" : " by " + amountExpr.toString(event, debug));
    }
}
