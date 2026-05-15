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
    protected @Nullable Boolean[] get(Event event) {
        DisplayData d = displayExpr.getSingle(event);
        if (!(d instanceof TextDisplayData text)) return null;

        Boolean value = switch (matchedPattern) {
            case 0 -> text.hasOutline();
            case 1 -> text.isSeeThrough();
            case 2 -> text.hasDefaultBackground();
            default -> null;
        };

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

        switch (matchedPattern) {
            case 0 -> text.setOutline(b);
            case 1 -> text.setSeeThrough(b);
            case 2 -> text.setDefaultBackground(b);
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Boolean> getReturnType() { return Boolean.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        String prop = switch (matchedPattern) {
            case 0 -> "outline";
            case 1 -> "see through";
            case 2 -> "default background";
            default -> "unknown";
        };
        return prop + " of " + displayExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(DisplayBoolean.class, Boolean.class)
                .addPattern("[the] outline of %displaydata%")            // 0
                .addPattern("[the] see[ ]through of %displaydata%")      // 1
                .addPattern("[the] default background of %displaydata%") // 2
                .build()
        );
    }
}
