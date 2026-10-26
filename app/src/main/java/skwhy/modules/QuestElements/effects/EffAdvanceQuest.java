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

@Name("[Quest] Advance Quest")
@Description("Moves players forward in a quest: to the step that follows their current one (the 'next' of the step), or to a given step (numbered from 1). " +
    "Players who do not have the quest yet unlock it first (and stay on its first step when no step is given). Steps whose condition is false are skipped, " +
    "and a step that does not exist (or has no objective) finishes the quest.")
@Examples({
    "advance quest \"chasseur\" for player",
    "advance quest \"chasseur\" for player to step 3"
})
@Since("1.6.0")
public class EffAdvanceQuest extends Effect {

    private Expression<Quest> questExpr;
    private Expression<OfflinePlayer> playersExpr;
    private @Nullable Expression<Number> stepExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffAdvanceQuest.class)
                .addPattern("advance %quest% for %offlineplayers% [to step %-number%]")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        questExpr = (Expression<Quest>) exprs[0];
        playersExpr = (Expression<OfflinePlayer>) exprs[1];
        stepExpr = (Expression<Number>) exprs[2];
        return true;
    }

    @Override
    protected void execute(Event event) {
        Quest quest = questExpr.getSingle(event);
        if (quest == null) return;
        Number step = stepExpr == null ? null : stepExpr.getSingle(event);
        for (OfflinePlayer player : playersExpr.getArray(event)) {
            // Sans étape donnée, débloquer une quête inconnue la place sur sa première étape : c'est déjà son premier pas
            boolean unknown = QuestEngine.step(player, quest) == -1;
            if (unknown) QuestEngine.unlock(player, quest);
            if (step != null) QuestEngine.setStep(player, quest, Math.max(0, step.intValue() - 1), false);
            else if (!unknown) QuestEngine.advance(player, quest);
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "advance " + questExpr.toString(event, debug) + " for " + playersExpr.toString(event, debug)
            + (stepExpr == null ? "" : " to step " + stepExpr.toString(event, debug));
    }
}
