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

@Name("[Quest] Quest Variable")
@Description("A variable of a quest for a player (numbers, texts or booleans). Only the variables the quest declares can be changed: " +
    "other names are ignored. Setting, adding and removing re-check the objectives of type 'variable' of the player. " +
    "Delete or reset removes the variable. A number variable that does not exist counts as 0 for add and remove.")
@Examples({
    "add 1 to quest variable \"locked\" of quest \"fermier\" for player",
    "set quest variable \"mana_help\" of quest \"main\" for player to 10",
    "if quest variable \"timer\" of quest \"main\" for player is not set:",
    "\tsend \"Pas encore commencé\"",
    "delete quest variable \"timer\" of quest \"main\" for player"
})
@Since("1.6.0")
public class ExprQuestVariable extends SimpleExpression<Object> {

    private Expression<String> nameExpr;
    private Expression<Quest> questExpr;
    private Expression<OfflinePlayer> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        nameExpr = (Expression<String>) exprs[0];
        questExpr = (Expression<Quest>) exprs[1];
        playerExpr = (Expression<OfflinePlayer>) exprs[2];
        return true;
    }

    @Override
    protected Object @Nullable [] get(Event event) {
        String name = nameExpr.getSingle(event);
        Quest quest = questExpr.getSingle(event);
        OfflinePlayer player = playerExpr.getSingle(event);
        if (name == null || quest == null || player == null) return null;
        Object value = QuestEngine.variable(player, quest.id(), name);
        return value == null ? null : new Object[]{ value };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case SET -> new Class<?>[]{ Object.class };
            case ADD, REMOVE -> new Class<?>[]{ Number.class };
            case DELETE, RESET -> new Class<?>[0];
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        String name = nameExpr.getSingle(event);
        Quest quest = questExpr.getSingle(event);
        OfflinePlayer player = playerExpr.getSingle(event);
        if (name == null || quest == null || player == null) return;
        switch (mode) {
            case DELETE, RESET -> QuestEngine.deleteVariable(player, quest, name);
            case SET -> {
                if (delta != null && delta.length > 0) QuestEngine.setVariable(player, quest, name, delta[0], QuestEngine.Op.SET);
            }
            case ADD -> {
                if (delta != null && delta.length > 0) QuestEngine.setVariable(player, quest, name, delta[0], QuestEngine.Op.ADD);
            }
            case REMOVE -> {
                if (delta != null && delta.length > 0) QuestEngine.setVariable(player, quest, name, delta[0], QuestEngine.Op.REMOVE);
            }
            default -> { }
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<?> getReturnType() { return Object.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "quest variable " + nameExpr.toString(event, debug) + " of " + questExpr.toString(event, debug)
            + " for " + playerExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprQuestVariable.class, Object.class)
                .addPattern("quest variable %string% of %quest% for %offlineplayer%")
                .build()
        );
    }
}
