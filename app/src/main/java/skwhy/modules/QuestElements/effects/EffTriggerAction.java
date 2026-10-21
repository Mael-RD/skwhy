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

@Name("[Quest] Trigger Quest Action / Recheck Objectives")
@Description("'trigger quest action' tells the quests that something happened: every objective of type action whose key is this text " +
    "(for example 'break.stone', 'kill.zombie' or any key you invent like 'chronolithe') progresses by the amount (1 by default) for these players. " +
    "'recheck quest objectives' recomputes the objectives of type special (jobs, quest, variable, advancement, rank), " +
    "of one type or of all types, for players: call it when what they depend on changed outside of the quest system.")
@Examples({
    "trigger quest action \"break.stone\" for player",
    "trigger quest action \"chronolithe\" with amount 2 for player",
    "recheck quest objectives of type \"jobs\" for player"
})
@Since("1.6.0")
public class EffTriggerAction extends Effect {

    private Expression<String> textExpr;
    private Expression<OfflinePlayer> playersExpr;
    private @Nullable Expression<Number> amountExpr;
    private boolean recheck;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffTriggerAction.class)
                .addPattern("trigger quest action %string% [with amount %-number%] for %offlineplayers%")
                .addPattern("recheck quest objectives [of type %-string%] for %offlineplayers%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        recheck = matchedPattern == 1;
        if (recheck) {
            textExpr = (Expression<String>) exprs[0];
            playersExpr = (Expression<OfflinePlayer>) exprs[1];
        } else {
            textExpr = (Expression<String>) exprs[0];
            amountExpr = (Expression<Number>) exprs[1];
            playersExpr = (Expression<OfflinePlayer>) exprs[2];
        }
        return true;
    }

    @Override
    protected void execute(Event event) {
        String text = textExpr == null ? null : textExpr.getSingle(event);
        Number amount = amountExpr == null ? null : amountExpr.getSingle(event);
        for (OfflinePlayer player : playersExpr.getArray(event)) {
            if (recheck) QuestEngine.recheckSpecials(player, text);
            else if (text != null) QuestEngine.triggerAction(player, text, amount == null ? 1 : amount.intValue());
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        if (recheck) {
            return "recheck quest objectives" + (textExpr == null ? "" : " of type " + textExpr.toString(event, debug)) + " for " + playersExpr.toString(event, debug);
        }
        return "trigger quest action " + textExpr.toString(event, debug) + (amountExpr == null ? "" : " with amount " + amountExpr.toString(event, debug))
            + " for " + playersExpr.toString(event, debug);
    }
}
