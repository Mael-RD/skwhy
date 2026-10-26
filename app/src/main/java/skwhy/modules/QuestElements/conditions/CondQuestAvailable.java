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
import skwhy.quest.model.QuestStatus;

@Name("[Quest] Is Available")
@Description("Checks if a quest is available for players: unlocked but without any progress yet.")
@Examples({
    "if quest \"chasseur\" is available for player:",
    "\tsend \"Une nouvelle quête t'attend\""
})
@Since("1.6.0")
public class CondQuestAvailable extends Condition {

    private Expression<Quest> questExpr;
    private Expression<OfflinePlayer> playersExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.CONDITION,
            SyntaxInfo.builder(CondQuestAvailable.class)
                .origin(Origin.of(addon))
                .addPattern("%quest% (is|are) available for %offlineplayers%")
                .addPattern("%quest% (is|are)(n't| not) available for %offlineplayers%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        questExpr = (Expression<Quest>) exprs[0];
        playersExpr = (Expression<OfflinePlayer>) exprs[1];
        setNegated(matchedPattern == 1);
        return true;
    }

    @Override
    public boolean check(Event event) {
        Quest quest = questExpr.getSingle(event);
        if (quest == null) return isNegated();
        return playersExpr.check(event, player -> QuestEngine.status(player, quest) == QuestStatus.AVAILABLE, isNegated());
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return questExpr.toString(event, debug) + (isNegated() ? " isn't" : " is") + " available for " + playersExpr.toString(event, debug);
    }
}
