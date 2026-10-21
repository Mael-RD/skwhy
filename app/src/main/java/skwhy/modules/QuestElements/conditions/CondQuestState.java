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

@Name("[Quest] Has Quest")
@Description("Checks if players have unlocked, started or finished a quest. 'unlocked' is true for any status but unknown (available, started or finished); " +
    "'started' is true while the quest is available or started and not finished yet.")
@Examples({
    "if player has finished quest \"chasseur\":",
    "\tsend \"Tu es chasseur confirmé\"",
    "if player hasn't unlocked quest \"main\":",
    "\tunlock quest \"main\" for player"
})
@Since("1.6.0")
public class CondQuestState extends Condition {

    private Expression<OfflinePlayer> playersExpr;
    private Expression<Quest> questExpr;
    private int kind; // 0 : débloquée, 1 : commencée, 2 : terminée

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.CONDITION,
            SyntaxInfo.builder(CondQuestState.class)
                .origin(Origin.of(addon))
                .addPattern("%offlineplayers% (has|have) (0:unlocked|1:started|2:finished) %quest%")
                .addPattern("%offlineplayers% (has|have)(n't| not) (0:unlocked|1:started|2:finished) %quest%")
                .addPattern("%offlineplayers% (do|does)(n't| not) have (0:unlocked|1:started|2:finished) %quest%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        playersExpr = (Expression<OfflinePlayer>) exprs[0];
        questExpr = (Expression<Quest>) exprs[1];
        kind = pr.mark;
        setNegated(matchedPattern >= 1);
        return true;
    }

    @Override
    public boolean check(Event event) {
        Quest quest = questExpr.getSingle(event);
        if (quest == null) return isNegated();
        return playersExpr.check(event, player -> {
            QuestStatus status = QuestEngine.status(player, quest);
            return switch (kind) {
                case 0 -> status != QuestStatus.UNKNOWN;
                case 1 -> status == QuestStatus.STARTED || status == QuestStatus.AVAILABLE;
                default -> status == QuestStatus.FINISHED;
            };
        }, isNegated());
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        String word = switch (kind) {
            case 0 -> "unlocked";
            case 1 -> "started";
            default -> "finished";
        };
        return playersExpr.toString(event, debug) + (isNegated() ? " hasn't " : " has ") + word + " " + questExpr.toString(event, debug);
    }
}
