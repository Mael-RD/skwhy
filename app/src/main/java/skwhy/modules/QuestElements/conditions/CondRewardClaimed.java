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

@Name("[Quest] Has Claimed Reward")
@Description("Checks if players already collected the reward of a quest.")
@Examples({
    "if player hasn't claimed reward of quest \"chasseur\":",
    "\tclaim reward of quest \"chasseur\" for player"
})
@Since("1.6.0")
public class CondRewardClaimed extends Condition {

    private Expression<OfflinePlayer> playersExpr;
    private Expression<Quest> questExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.CONDITION,
            SyntaxInfo.builder(CondRewardClaimed.class)
                .origin(Origin.of(addon))
                .addPattern("%offlineplayers% (has|have) claimed [the] reward of %quest%")
                .addPattern("%offlineplayers% (has|have)(n't| not) claimed [the] reward of %quest%")
                .addPattern("%offlineplayers% (do|does)(n't| not) have claimed [the] reward of %quest%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        playersExpr = (Expression<OfflinePlayer>) exprs[0];
        questExpr = (Expression<Quest>) exprs[1];
        setNegated(matchedPattern >= 1);
        return true;
    }

    @Override
    public boolean check(Event event) {
        Quest quest = questExpr.getSingle(event);
        if (quest == null) return isNegated();
        return playersExpr.check(event, player -> QuestEngine.rewardClaimed(player, quest), isNegated());
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return playersExpr.toString(event, debug) + (isNegated() ? " hasn't" : " has") + " claimed reward of " + questExpr.toString(event, debug);
    }
}
