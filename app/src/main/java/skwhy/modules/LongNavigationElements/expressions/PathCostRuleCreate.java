package skwhy.modules.LongNavigationElements.expressions;

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

import skwhy.modules.LongNavigationElements.types.PathCostRuleData;

@Name("[ChunkPath] Path Cost Rule Creation")
@Description("Creates a new, empty path cost rule (no blocks/tags yet), optionally with an initial cost (0 if " +
    "omitted). Populate it with 'blocks of path cost rule %pathcostrules%' and 'tags of path cost rule " +
    "%pathcostrules%', then pass one or more rules to 'register chunk path of %chunk% using rules %pathcostrules%'.")
@Examples({
    "set {_rule} to a new path cost rule with cost 1",
    "add stone and cobblestone to blocks of path cost rule {_rule}",
    "add minecraft tag \"leaves\" to tags of path cost rule {_rule}"
})
@Since("1.4.0")
public class PathCostRuleCreate extends SimpleExpression<PathCostRuleData> {

    private Expression<Number> costExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.costExpr = exprs.length > 0 ? (Expression<Number>) exprs[0] : null;
        return true;
    }

    @Override
    protected PathCostRuleData @Nullable [] get(Event event) {
        int cost = 0;
        if (costExpr != null) {
            Number n = costExpr.getSingle(event);
            if (n != null) cost = n.intValue();
        }
        return new PathCostRuleData[]{ new PathCostRuleData(cost) };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends PathCostRuleData> getReturnType() { return PathCostRuleData.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "a new path cost rule";
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(PathCostRuleCreate.class, PathCostRuleData.class)
                .addPattern("a new path cost rule [with cost %-number%]")
                .build()
        );
    }
}
