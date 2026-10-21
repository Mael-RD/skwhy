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

@Name("[Quest] Unlock / Reset Quest")
@Description("'unlock' makes a quest known to players and places them on its first step (nothing happens when they already know it). " +
    "'reset' makes them forget the quest: its progress and variables are deleted and they can unlock it again.")
@Examples({
    "unlock quest \"chasseur\" for player",
    "reset quest \"chasseur\" for all players"
})
@Since("1.6.0")
public class EffUnlockQuest extends Effect {

    private Expression<Quest> questExpr;
    private Expression<OfflinePlayer> playersExpr;
    private boolean reset;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffUnlockQuest.class)
                .addPattern("unlock %quest% for %offlineplayers%")
                .addPattern("reset %quest% for %offlineplayers%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        questExpr = (Expression<Quest>) exprs[0];
        playersExpr = (Expression<OfflinePlayer>) exprs[1];
        reset = matchedPattern == 1;
        return true;
    }

    @Override
    protected void execute(Event event) {
        Quest quest = questExpr.getSingle(event);
        if (quest == null) return;
        for (OfflinePlayer player : playersExpr.getArray(event)) {
            if (reset) QuestEngine.reset(player, quest);
            else QuestEngine.unlock(player, quest);
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return (reset ? "reset " : "unlock ") + questExpr.toString(event, debug) + " for " + playersExpr.toString(event, debug);
    }
}
