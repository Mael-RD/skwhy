package skwhy.modules.LongNavigationElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.chunkpath.block.PathCostRules;
import skwhy.chunkpath.longnav.LongNavigation;
import skwhy.modules.LongNavigationElements.types.PathCostRuleData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Name("[Long Navigation] Path Cost Rules")
@Description("Gets, sets, adds to, removes from, or clears the path cost rules a long navigation uses to walk. " +
    "A rule without any block or tag is the default rule (its cost applies to every block no other rule covers); " +
    "a block covered by no rule is not walkable. There are no default rules: a navigation without any rule logs a warning " +
    "and does nothing (it never walks). The getter returns copies: change a rule through 'cost of path cost rule' / 'blocks of path cost rule' " +
    "then 'set' it back. 'remove' matches rules by content (same blocks, tags and cost); 'delete' and 'reset' remove all the rules " +
    "(the navigation stops working), and so does a set/remove that leaves the list empty. A change recomputes the current path of the " +
    "navigation. Only the walking is affected: the route between chunks keeps the costs of the chunk path records.")
@Examples({
    "set {_road} to a new path cost rule with cost 1",
    "add stone bricks to blocks of path cost rule {_road}",
    "set {_other} to a new path cost rule with cost 5",
    "set path cost rules of long navigation {_longnav} to {_road} and {_other}",
    "add {_road} to path cost rules of long navigation {_longnav}",
    "remove {_road} from path cost rules of long navigation {_longnav}",
    "set {_rules::*} to path cost rules of long navigation {_longnav}",
    "delete path cost rules of long navigation {_longnav}"
})
@Since("1.5.0")
public class LongNavigationRules extends SimpleExpression<PathCostRuleData> {

    private Expression<LongNavigation> navigationExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.navigationExpr = (Expression<LongNavigation>) exprs[0];
        return true;
    }

    @Override
    protected PathCostRuleData @Nullable [] get(Event event) {
        LongNavigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return null;
        List<PathCostRuleData> result = new ArrayList<>();
        PathCostRules current = navigation.getCostRules();
        if (current == null) return new PathCostRuleData[0];
        for (PathCostRules.CostRule rule : current.rules()) {
            result.add(PathCostRuleData.fromCostRule(rule));
        }
        return result.toArray(new PathCostRuleData[0]);
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case SET, ADD, REMOVE, REMOVE_ALL -> new Class<?>[]{ PathCostRuleData.class, PathCostRuleData[].class };
            case DELETE, RESET -> new Class<?>[0];
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        LongNavigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return;

        List<PathCostRules.CostRule> given = new ArrayList<>();
        if (delta != null) {
            for (Object value : delta) {
                if (value instanceof PathCostRuleData rule) given.add(rule.toCostRule());
                else if (value instanceof PathCostRuleData[] array) {
                    for (PathCostRuleData rule : array) if (rule != null) given.add(rule.toCostRule());
                }
            }
        }

        List<PathCostRules.CostRule> result;
        switch (mode) {
            case SET -> result = given;
            case ADD -> {
                result = navigation.getCostRules() == null ? new ArrayList<>() : new ArrayList<>(navigation.getCostRules().rules());
                result.addAll(given);
            }
            case REMOVE, REMOVE_ALL -> {
                Set<String> toRemove = new HashSet<>();
                for (PathCostRules.CostRule rule : given) toRemove.add(rule.signature());
                result = new ArrayList<>();
                if (navigation.getCostRules() != null) {
                    for (PathCostRules.CostRule rule : navigation.getCostRules().rules()) {
                        if (!toRemove.contains(rule.signature())) result.add(rule);
                    }
                }
            }
            case DELETE, RESET -> result = List.of();
            default -> { return; }
        }
        navigation.setCostRules(result.isEmpty() ? null : new PathCostRules(result));
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends PathCostRuleData> getReturnType() { return PathCostRuleData.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "path cost rules of " + navigationExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(LongNavigationRules.class, PathCostRuleData.class)
                .addPattern("path cost rules of long navigation %longnavigation%")
                .build()
        );
    }
}
