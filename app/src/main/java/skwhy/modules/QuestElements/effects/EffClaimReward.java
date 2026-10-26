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

@Name("[Quest] Claim Reward")
@Description("Gives players the reward of a quest they finished (the 'reward' action of the quest script), once. " +
    "Nothing happens when the quest is not finished, has no reward or when the reward was already claimed.")
@Examples({
    "claim reward of quest \"chasseur\" for player"
})
@Since("1.6.0")
public class EffClaimReward extends Effect {

    private Expression<Quest> questExpr;
    private Expression<OfflinePlayer> playersExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffClaimReward.class)
                .addPattern("claim reward of %quest% for %offlineplayers%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        questExpr = (Expression<Quest>) exprs[0];
        playersExpr = (Expression<OfflinePlayer>) exprs[1];
        return true;
    }

    @Override
    protected void execute(Event event) {
        Quest quest = questExpr.getSingle(event);
        if (quest == null) return;
        for (OfflinePlayer player : playersExpr.getArray(event)) QuestEngine.claimReward(player, quest);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "claim reward of " + questExpr.toString(event, debug) + " for " + playersExpr.toString(event, debug);
    }
}
