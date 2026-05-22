package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.DisplayData;

public class DisplayId extends SimpleExpression<Object> {

    // 0 → entity id  (Number)
    // 1 → uuid       (String)
    private int matchedPattern;
    private Expression<DisplayData> displayExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.matchedPattern = matchedPattern;
        this.displayExpr    = (Expression<DisplayData>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Object[] get(Event event) {
        DisplayData d = displayExpr.getSingle(event);
        if (d == null) return null;

        return switch (matchedPattern) {
            case 0 -> new Number[]{ d.getEntityId() };
            case 1 -> new String[]{ d.getEntityUUID().toString() };
            default -> null;
        };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<?> getReturnType() {
        return matchedPattern == 0 ? Number.class : String.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return (matchedPattern == 0 ? "entity id" : "uuid")
            + " of " + displayExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(DisplayId.class, Object.class)
                .addPattern("[the] entity id of %displaydata%")   // 0
                .addPattern("[the] entity uuid of %displaydata%") // 1
                .build()
        );
    }
}