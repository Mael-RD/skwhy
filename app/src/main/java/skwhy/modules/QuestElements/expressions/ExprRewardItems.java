package skwhy.modules.QuestElements.expressions;

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
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.model.Quest;
import skwhy.quest.script.ScriptContext;
import skwhy.quest.script.ScriptEngine;

@Name("[Quest] Reward Items")
@Description("The items the player would receive from the reward of a quest ('give' actions of its reward script, following the lists whose conditions are true for the player). " +
    "Nothing is given and nothing happens: use it to describe the reward in a menu.")
@Examples({
    "loop reward items of quest \"chasseur\" for player:",
    "\tadd \"%item amount of loop-value% %loop-value%\" to {_lore::*}"
})
@Since("1.6.0")
public class ExprRewardItems extends SimpleExpression<ItemStack> {

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
    protected ItemStack @Nullable [] get(Event event) {
        Quest quest = questExpr.getSingle(event);
        OfflinePlayer player = playerExpr.getSingle(event);
        if (quest == null || player == null) return new ItemStack[0];
        return ScriptEngine.rewardItems(ScriptContext.forQuest(player, quest)).toArray(new ItemStack[0]);
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends ItemStack> getReturnType() { return ItemStack.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "reward items of " + questExpr.toString(event, debug) + " for " + playerExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprRewardItems.class, ItemStack.class)
                .addPattern("reward items of %quest% for %offlineplayer%")
                .build()
        );
    }
}
