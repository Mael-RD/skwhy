package skwhy.modules.QuestElements.expressions;

import ch.njol.skript.classes.Changer.ChangeMode;
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
import skwhy.quest.data.PlayerQuestData;
import skwhy.quest.data.QuestProgress;
import skwhy.quest.data.QuestStore;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestStatus;

@Name("[Quest] Quest Status")
@Description("The status of a quest for a player: unknown (never unlocked), available (unlocked, no progress yet), started or finished. " +
    "Can be set: 'available', 'started' and 'finished' only change the status (the step and the progress are kept; use 'quest step' to move " +
    "the player), 'unknown' and reset forget the quest like 'reset <quest> for <player>'.")
@Examples({
    "if quest status of quest \"chasseur\" for player is finished:",
    "\tsend \"Bravo !\"",
    "set quest status of quest \"chasseur\" for player to started",
    "reset quest status of quest \"chasseur\" for player"
})
@Since("1.6.0")
public class ExprQuestStatus extends SimpleExpression<QuestStatus> {

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
    protected QuestStatus @Nullable [] get(Event event) {
        Quest quest = questExpr.getSingle(event);
        OfflinePlayer player = playerExpr.getSingle(event);
        if (quest == null || player == null) return null;
        return new QuestStatus[]{ QuestEngine.status(player, quest) };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ QuestStatus.class };
        if (mode == ChangeMode.RESET || mode == ChangeMode.DELETE) return new Class<?>[0];
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        Quest quest = questExpr.getSingle(event);
        OfflinePlayer player = playerExpr.getSingle(event);
        if (quest == null || player == null) return;
        if (mode != ChangeMode.SET) {
            QuestEngine.reset(player, quest);
            return;
        }
        if (delta == null || delta.length == 0 || !(delta[0] instanceof QuestStatus status)) return;
        if (status == QuestStatus.UNKNOWN) {
            QuestEngine.reset(player, quest);
            return;
        }
        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        QuestProgress progress = data.quests.get(quest.id());
        if (progress == null) {
            QuestEngine.unlock(player, quest);
            progress = data.quests.get(quest.id());
            if (progress == null) return;
        }
        progress.status = status;
        data.markDirty();
        QuestEngine.reindex(data);
        QuestEngine.refresh(player);
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends QuestStatus> getReturnType() { return QuestStatus.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "quest status of " + questExpr.toString(event, debug) + " for " + playerExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprQuestStatus.class, QuestStatus.class)
                .addPattern("quest status of %quest% for %offlineplayer%")
                .build()
        );
    }
}
