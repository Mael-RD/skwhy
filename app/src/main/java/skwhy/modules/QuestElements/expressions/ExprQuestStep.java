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
import skwhy.quest.model.Quest;

@Name("[Quest] Quest Step")
@Description("The step a player is on in a quest, numbered from 1 (0 when the player has not started the quest). " +
    "A finished quest is on the step after the last one. Setting it moves the player: steps whose condition is false are skipped, " +
    "the objectives of the new step start from zero and a step without objective finishes the quest. " +
    "Setting it to 0 or resetting it forgets the quest. Add and remove move relatively to the current step.")
@Examples({
    "send \"Etape %quest step of quest \"chasseur\" for player%\"",
    "set quest step of quest \"chasseur\" for player to 3",
    "add 1 to quest step of quest \"chasseur\" for player",
    "reset quest step of quest \"chasseur\" for player"
})
@Since("1.6.0")
public class ExprQuestStep extends SimpleExpression<Integer> {

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
    protected Integer @Nullable [] get(Event event) {
        Quest quest = questExpr.getSingle(event);
        OfflinePlayer player = playerExpr.getSingle(event);
        if (quest == null || player == null) return null;
        return new Integer[]{ QuestEngine.step(player, quest) + 1 };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case SET, ADD, REMOVE -> new Class<?>[]{ Number.class };
            case RESET, DELETE -> new Class<?>[0];
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        Quest quest = questExpr.getSingle(event);
        OfflinePlayer player = playerExpr.getSingle(event);
        if (quest == null || player == null) return;
        if (mode == ChangeMode.RESET || mode == ChangeMode.DELETE) {
            QuestEngine.reset(player, quest);
            return;
        }
        if (delta == null || delta.length == 0 || !(delta[0] instanceof Number number)) return;
        int value = number.intValue();
        int current = QuestEngine.step(player, quest);
        int target = switch (mode) {
            case ADD -> Math.max(-1, current + value);
            case REMOVE -> Math.max(-1, current - value);
            default -> value - 1;
        };
        if (target < 0) QuestEngine.reset(player, quest);
        else if (current == -1) {
            QuestEngine.unlock(player, quest);
            if (target > 0) QuestEngine.setStep(player, quest, target, false);
        } else {
            QuestEngine.setStep(player, quest, target, false);
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Integer> getReturnType() { return Integer.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "quest step of " + questExpr.toString(event, debug) + " for " + playerExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprQuestStep.class, Integer.class)
                .addPattern("quest step of %quest% for %offlineplayer%")
                .build()
        );
    }
}
