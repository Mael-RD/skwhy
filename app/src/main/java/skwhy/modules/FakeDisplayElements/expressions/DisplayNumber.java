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
import skwhy.data.ItemDisplayData;
import skwhy.data.TextDisplayData;

public class DisplayNumber extends SimpleExpression<Number> {

    // ── Indices des patterns ───────────────────────────────────────────────
    // 0  glow color          → tous
    // 1  shadow radius       → tous
    // 2  shadow strength     → tous
    // 3  view range          → tous
    // 4  billboard mode      → tous
    // 5  display mode        → ItemDisplayData seulement
    // 6  text color          → TextDisplayData seulement
    // 7  background color    → TextDisplayData seulement
    // 8  text alignment      → TextDisplayData seulement
    // 9  line width          → TextDisplayData seulement

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
    protected @Nullable Number[] get(Event event) {
        DisplayData d = displayExpr.getSingle(event);
        if (d == null) return null;

        Number value = switch (matchedPattern) {
            case 0 -> d.getGlowColor();
            case 1 -> d.getShadowRadius();
            case 2 -> d.getShadowStrength();
            case 3 -> d.getViewRange();
            case 4 -> d.getBillboardMode();
            case 5 -> d instanceof ItemDisplayData item ? item.getDisplayMode() : null;
            case 6 -> d instanceof TextDisplayData  text ? text.getBackgroundColor() : null;
            case 7 -> d instanceof TextDisplayData  text ? text.getTextAlignment()   : null;
            case 8 -> d instanceof TextDisplayData  text ? text.getLineWidth()       : null;
            default -> null;
        };

        return value != null ? new Number[]{ value } : null;
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ Number.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || !(delta[0] instanceof Number n)) return;
        DisplayData d = displayExpr.getSingle(event);
        if (d == null) return;

        switch (matchedPattern) {
            case 0 -> d.setGlowColor(n.intValue());
            case 1 -> d.setShadowRadius(n.floatValue());
            case 2 -> d.setShadowStrength(n.floatValue());
            case 3 -> d.setViewRange(n.floatValue());
            case 4 -> d.setBillboardMode(n.intValue());
            case 5 -> { if (d instanceof ItemDisplayData item) item.setDisplayMode(n.intValue()); }
            case 6 -> { if (d instanceof TextDisplayData  text) text.setBackgroundColor(n.intValue()); }
            case 7 -> { if (d instanceof TextDisplayData  text) text.setTextAlignment(n.intValue()); }
            case 8 -> { if (d instanceof TextDisplayData  text) text.setLineWidth(n.intValue()); }
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Number> getReturnType() { return Number.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        DisplayData d = displayExpr.getSingle(event);
        if (d == null) return null;
        return switch (matchedPattern) {
            case 0 -> Integer.toString(d.getGlowColor());
            case 1 -> Float.toString(d.getShadowRadius());
            case 2 -> Float.toString(d.getShadowStrength());
            case 3 -> Float.toString(d.getViewRange());
            case 4 -> Integer.toString(d.getBillboardMode());
            case 5 -> d instanceof ItemDisplayData item ? Integer.toString(item.getDisplayMode()) : null;
            case 6 -> d instanceof TextDisplayData text ? Integer.toString(text.getBackgroundColor()) : null;
            case 7 -> d instanceof TextDisplayData text ? Integer.toString(text.getTextAlignment()) : null;
            case 8 -> d instanceof TextDisplayData text ? Integer.toString(text.getLineWidth()) : null;
            default -> null;
        };
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(DisplayNumber.class, Number.class)
                .addPattern("[the] glow [color] of %displaydata%")           // 0
                .addPattern("[the] shadow radius of %displaydata%")           // 1
                .addPattern("[the] shadow strength of %displaydata%")         // 2
                .addPattern("[the] view range of %displaydata%")              // 3
                .addPattern("[the] billboard [mode] of %displaydata%")        // 4
                .addPattern("[the] display mode of %displaydata%")            // 5
                .addPattern("[the] background color of %displaydata%")        // 6
                .addPattern("[the] text alignment of %displaydata%")          // 7
                .addPattern("[the] line width of %displaydata%")              // 8
                .build()
        );
    }
}
