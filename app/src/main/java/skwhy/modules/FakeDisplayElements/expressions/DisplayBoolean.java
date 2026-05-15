package skwhy.modules.FakeDisplayElements.expressions;

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
import skwhy.data.DisplayData;
import skwhy.data.TextDisplayData;

public class DisplayBoolean extends SimpleExpression<Boolean> {

    // 0  outline            → TextDisplayData seulement
    // 1  see through        → TextDisplayData seulement
    // 2  default background → TextDisplayData seulement

    private Expression<DisplayData> displayExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.displayExpr    = (Expression<DisplayData>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Boolean[] get(Event event) {
        DisplayData d = displayExpr.getSingle(event);
        if (!(d instanceof TextDisplayData text)) return null;
        Boolean value = text.isSeeThrough();

        return value != null ? new Boolean[]{ value } : null;
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ Boolean.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || !(delta[0] instanceof Boolean b)) return;
        DisplayData d = displayExpr.getSingle(event);
        if (!(d instanceof TextDisplayData text)) return;
        text.setSeeThrough(b);
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Boolean> getReturnType() { return Boolean.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        DisplayData d = displayExpr.getSingle(event);
        if (!(d instanceof TextDisplayData text)) return null;

        return text.isSeeThrough() ? "true" : "false";
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(DisplayBoolean.class, Boolean.class)
                .addPattern("[the] see[ ]through of %displaydata%")
                .build()
        );
    }
}
