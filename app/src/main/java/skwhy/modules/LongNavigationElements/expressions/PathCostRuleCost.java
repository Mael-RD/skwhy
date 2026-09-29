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

import skwhy.modules.LongNavigationElements.types.PathCostRuleData;

@Name("[ChunkPath] Path Cost Rule Cost")
@Description("Gets or sets the movement cost applied by a path cost rule when one of its blocks/tags matches. " +
    "Unlike the rule's blocks/tags lists, this does not support 'add', 'remove', 'delete', or 'reset' — only get and set.")
@Examples({
    "set {_rule} to a new path cost rule",
    "set cost of path cost rule {_rule} to 1",
    "broadcast \"cost: %cost of path cost rule {_rule}%\""
})
@Since("1.4.0")
public class PathCostRuleCost extends SimpleExpression<Number> {

    private Expression<PathCostRuleData> ruleExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.ruleExpr = (Expression<PathCostRuleData>) exprs[0];
        return true;
    }

    @Override
    protected Number @Nullable [] get(Event event) {
        PathCostRuleData rule = ruleExpr.getSingle(event);
        if (rule == null) return null;
        return new Number[]{ rule.getCost() };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ Number.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || delta.length == 0) return;
        if (!(delta[0] instanceof Number n)) return;
        PathCostRuleData rule = ruleExpr.getSingle(event);
        if (rule == null) return;
        rule.setCost(n.intValue());
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Number> getReturnType() { return Number.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "cost of path cost rule " + ruleExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(PathCostRuleCost.class, Number.class)
                .addPattern("cost of path cost rule %pathcostrules%")
                .build()
        );
    }
}
