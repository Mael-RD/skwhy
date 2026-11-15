package skwhy.modules.QuestElements.expressions;

import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import skwhy.quest.Snbt;
import skwhy.quest.script.QuestItemEntries;

@Name("[Quest] Quest Item Entry")
@Description("An item written as an entry of a quest or NPC definition ({item: ..., count: n}), as text (SNBT, readable with 'nbt compound from') to put in the 'items', 'result' or 'buy1' of a definition. " +
    "A custom item left as its definition gives its id ({item: \"billet\", count: 2}, plus 'parameters' such as owner or safe), a plain item its material ({item: \"stone\", count: 1}); " +
    "any other item (a vanilla item with a name or enchantments, a custom item that differs from its definition such as a banknote of another value) is written as a definition " +
    "({item: {material_id: ..., name: ...}}) that the quest turns back into an item with no id. The count is the amount of the item. Nothing is set for an empty slot.")
@Examples({
    "set {_entry} to quest item entry of tool of player",
    "set {_entry} to quest item entry of custom item \"billet\""
})
@Since("1.6.0")
public class ExprQuestItemEntry extends SimpleExpression<String> {

    private Expression<ItemStack> item;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprQuestItemEntry.class, String.class)
                .addPattern("quest item entry of %itemstack%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        item = (Expression<ItemStack>) exprs[0];
        return true;
    }

    @Override
    protected String @Nullable [] get(Event event) {
        ItemStack stack = item.getSingle(event);
        if (stack == null || stack.getType().isAir()) return new String[0];
        return new String[]{ Snbt.write(QuestItemEntries.entryOf(stack, stack.getAmount())) };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "quest item entry of " + item.toString(event, debug);
    }
}
