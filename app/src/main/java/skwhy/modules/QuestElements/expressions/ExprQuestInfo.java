package skwhy.modules.QuestElements.expressions;

import java.util.ArrayList;
import java.util.List;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.ItemIcons;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestObjective;
import skwhy.quest.model.QuestStep;

@Name("[Quest] Quest Info")
@Description("Properties of a quest definition: its id, name (with its color codes), type (principale, secondaire or contrée), " +
    "category (villes, quete_zelda...), priority, icon (an item), number of steps, declared variables, and the text of a step (numbered from 1). " +
    "It also gives the text, total, kind and number of a quest objective: 'quest objective text of ...' is what the player reads, " +
    "'count' the total asked, 'type' one of external, action, item, special, 'key' the action key (break.stone) or the item id, " +
    "and 'number' its number in the step (from 1).")
@Examples({
    "send quest name of quest \"chasseur\"",
    "set {_icon} to quest icon of quest \"chasseur\"",
    "send quest text of step 1 of quest \"chasseur\"",
    "loop current objectives of quest \"chasseur\" for player:",
    "\tsend \"%quest objective text of loop-value% (%quest objective count of loop-value%)\""
})
@Since("1.6.0")
public class ExprQuestInfo extends SimpleExpression<Object> {

    private static final String[] QUEST_PROPERTIES = {"id", "name", "type", "category", "priority", "icon", "step count", "variables"};
    private static final String[] OBJECTIVE_PROPERTIES = {"text", "count", "type", "key", "number"};

    private int property;
    private int kind; // 0 : quête, 1 : texte d'étape, 2 : objectif
    private Expression<?> source;
    private @Nullable Expression<Number> stepExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        kind = matchedPattern;
        if (kind == 0) {
            property = pr.mark;
            source = exprs[0];
        } else if (kind == 1) {
            stepExpr = (Expression<Number>) exprs[0];
            source = exprs[1];
        } else {
            property = pr.mark;
            source = exprs[0];
        }
        return true;
    }

    @Override
    protected Object @Nullable [] get(Event event) {
        Object value = source.getSingle(event);
        if (value == null) return null;
        if (kind == 0 && value instanceof Quest quest) {
            return switch (QUEST_PROPERTIES[property]) {
                case "id" -> new Object[]{ quest.id() };
                case "name" -> new Object[]{ quest.name() };
                case "type" -> new Object[]{ quest.type() };
                case "category" -> new Object[]{ quest.category() };
                case "priority" -> new Object[]{ quest.priority() };
                case "icon" -> {
                    org.bukkit.inventory.ItemStack icon = ItemIcons.of(quest);
                    yield icon == null ? null : new Object[]{ icon };
                }
                case "step count" -> new Object[]{ quest.steps().size() };
                default -> quest.variables().toArray();
            };
        }
        if (kind == 1 && value instanceof Quest quest) {
            Number n = stepExpr == null ? null : stepExpr.getSingle(event);
            if (n == null) return null;
            QuestStep step = quest.step(n.intValue() - 1);
            return step == null ? null : new Object[]{ step.text() };
        }
        if (kind == 2 && value instanceof QuestObjective objective) {
            return switch (OBJECTIVE_PROPERTIES[property]) {
                case "text" -> new Object[]{ objective.text() };
                case "count" -> new Object[]{ objective.count() };
                case "type" -> new Object[]{ objective.type().name().toLowerCase() };
                case "key" -> {
                    List<Object> out = new ArrayList<>();
                    String key = objective.key() != null ? objective.key() : objective.special();
                    if (key != null) out.add(key);
                    yield out.toArray();
                }
                default -> new Object[]{ objective.index() + 1 };
            };
        }
        return null;
    }

    @Override
    public boolean isSingle() { return !(kind == 0 && property == 7); }

    @Override
    public Class<?> getReturnType() { return Object.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        if (kind == 1) return "quest text of step " + (stepExpr == null ? "?" : stepExpr.toString(event, debug)) + " of " + source.toString(event, debug);
        String name = kind == 0 ? QUEST_PROPERTIES[property] : OBJECTIVE_PROPERTIES[property];
        return (kind == 0 ? "quest " : "quest objective ") + name + " of " + source.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprQuestInfo.class, Object.class)
                .addPattern("quest (0:id|1:name|2:type|3:category|4:priority|5:icon|6:step count|7:variables) of %quest%")
                .addPattern("quest text of step %number% of %quest%")
                .addPattern("quest objective (0:text|1:count|2:type|3:key|4:number) of %questobjective%")
                .build()
        );
    }
}
