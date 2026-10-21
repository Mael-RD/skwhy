package skwhy.modules.QuestElements.conditions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.docs.Origin;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.QuestEngine;
import skwhy.quest.model.Quest;

@Name("[Quest] Is At Step")
@Description("Checks if players are on a given step of a quest (steps are numbered from 1).")
@Examples({
    "if player is at step 2 of quest \"chasseur\":",
    "\tsend \"Va chasser !\""
})
@Since("1.6.0")
public class CondAtStep extends Condition {

    private Expression<OfflinePlayer> playersExpr;
    private Expression<Number> stepExpr;
    private Expression<Quest> questExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.CONDITION,
            SyntaxInfo.builder(CondAtStep.class)
                .origin(Origin.of(addon))
                .addPattern("%offlineplayers% (is|are) at step %number% of %quest%")
                .addPattern("%offlineplayers% (is|are)(n't| not) at step %number% of %quest%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        playersExpr = (Expression<OfflinePlayer>) exprs[0];
        stepExpr = (Expression<Number>) exprs[1];
        questExpr = (Expression<Quest>) exprs[2];
        setNegated(matchedPattern == 1);
        return true;
    }

    @Override
    public boolean check(Event event) {
        Quest quest = questExpr.getSingle(event);
        Number step = stepExpr.getSingle(event);
        if (quest == null || step == null) return isNegated();
        return playersExpr.check(event, player -> QuestEngine.step(player, quest) + 1 == step.intValue(), isNegated());
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return playersExpr.toString(event, debug) + (isNegated() ? " isn't" : " is") + " at step " + stepExpr.toString(event, debug)
            + " of " + questExpr.toString(event, debug);
    }
}
