package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.doc.RequiredPlugins;

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

@Name("Display Numeric Properties")
@Description("Gets or sets numeric properties of display entities, including interpolation start, interpolation duration, and teleportation duration. Targets can be one or more displays or a display group.")
@Examples({
    "set {_display} to a new fake item display:",
    "    item: dirt",
    "set {_group} to a new fake display group at player from {_display}",
    "",
    "# A group target applies to each display it contains",
    "set fake display interpolation duration of {_group} to 5",
    "",
    "set fake display glow of {_display} to 16711680",
    "set fake display brightness of {_display} to 12",
    "delete fake display brightness of {_display} # Restore world lighting",
    "set fake display interpolation start of {_display} to 0",
    "set fake display interpolation duration of {_display} to 5",
    "set fake display teleportation duration of {_display} to 2",
    "set fake display shadow radius of {_display} to 0.5",
    "set fake display shadow strength of {_display} to 1.0",
    "set fake display view range of {_display} to 64",
    "set fake display billboard of {_display} to 0",
    "set fake display display mode of {_display} to 5",
    "set fake display text alignment of {_display} to 0",
    "set fake display line width of {_display} to 200",
    "set fake display interpolation start of {_display} to 0",
    "set fake display interpolation duration of {_display} to 5",
    "set fake display teleportation duration of {_display} to 2"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class DisplayNumber extends SimpleExpression<Number> {

    // ── Indices des patterns ───────────────────────────────────────────────
    // 0  glow color          → tous
    // 1  shadow radius       → tous
    // 2  shadow strength     → tous
    // 3  view range          → tous
    // 4  billboard mode      → tous
    // 5  display mode        → ItemDisplayData seulement
    // 6  text alignment      → TextDisplayData seulement
    // 7  line width          → TextDisplayData seulement
    // 8  brightness          → tous (nullable)
    // 9  interpolation start → tous
    // 10 interpolation duration → tous
    // 11 teleportation duration → tous

    private int matchedPattern;
    private boolean pluralTargets;
    private Expression<?> targets;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.pluralTargets = matchedPattern >= 12;
        this.matchedPattern = matchedPattern % 12;
        this.targets = exprs[0];
        return true;
    }

    @Override
    protected @Nullable Number[] get(Event event) {
        if (pluralTargets) return null;
        Object value = targets.getSingle(event);
        if (!(value instanceof DisplayData display)) return null;
        Number result = getValue(display);
        return result == null ? null : new Number[]{ result };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ Number.class };
        if (matchedPattern == 8 && (mode == ChangeMode.DELETE || mode == ChangeMode.RESET)) {
            return new Class<?>[0];
        }
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (matchedPattern == 8 && (mode == ChangeMode.DELETE || mode == ChangeMode.RESET)) {
            for (DisplayData display : DisplayExpressionTargets.resolveForChange(targets, event, pluralTargets)) {
                display.setBrightness(null);
            }
            return;
        }
        if (mode != ChangeMode.SET || delta == null || !(delta[0] instanceof Number n)) return;

        if (matchedPattern == 8) {
            if (!DisplayData.isValidBrightness(n)) {
                Skript.error("Display brightness must be an integer between 0 and 15; received " + n + ".");
                return;
            }
            for (DisplayData display : DisplayExpressionTargets.resolveForChange(targets, event, pluralTargets)) {
                display.setBrightness(n.intValue());
            }
            return;
        }

        for (DisplayData display : DisplayExpressionTargets.resolveForChange(targets, event, pluralTargets)) {
            switch (matchedPattern) {
                case 0 -> display.setGlowColor(n.intValue());
                case 1 -> display.setShadowRadius(n.floatValue());
                case 2 -> display.setShadowStrength(n.floatValue());
                case 3 -> display.setViewRange(n.floatValue());
                case 4 -> display.setBillboardMode(n.intValue());
                case 5 -> { if (display instanceof ItemDisplayData item) item.setDisplayMode(n.intValue()); }
                case 6 -> { if (display instanceof TextDisplayData text) text.setTextAlignment(n.intValue()); }
                case 7 -> { if (display instanceof TextDisplayData text) text.setLineWidth(n.intValue()); }
                case 9 -> display.setInterpolationStart(n.intValue());
                case 10 -> display.setInterpolationDuration(n.intValue());
                case 11 -> display.setTeleportationDuration(n.intValue());
            }
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Number> getReturnType() { return Number.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "display numeric property of " + targets.toString(event, debug);
    }

    private Number getValue(DisplayData d) {
        return switch (matchedPattern) {
            case 0 -> d.getGlowColor();
            case 1 -> d.getShadowRadius();
            case 2 -> d.getShadowStrength();
            case 3 -> d.getViewRange();
            case 4 -> d.getBillboardMode();
            case 5 -> d instanceof ItemDisplayData item ? item.getDisplayMode() : null;
            case 6 -> d instanceof TextDisplayData text ? text.getTextAlignment() : null;
            case 7 -> d instanceof TextDisplayData text ? text.getLineWidth() : null;
            case 8 -> d.getBrightness();
            case 9 -> d.getInterpolationStart();
            case 10 -> d.getInterpolationDuration();
            case 11 -> d.getTeleportationDuration();
            default -> null;
        };
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(DisplayNumber.class, Number.class)
                .addPattern("[the] fake display glow [color] of %displaydata/displaygroup%")           // 0
                .addPattern("[the] fake display shadow radius of %displaydata/displaygroup%")           // 1
                .addPattern("[the] fake display shadow strength of %displaydata/displaygroup%")         // 2
                .addPattern("[the] fake display view range of %displaydata/displaygroup%")              // 3
                .addPattern("[the] fake display billboard [mode] of %displaydata/displaygroup%")        // 4
                .addPattern("[the] fake display display mode of %displaydata/displaygroup%")            // 5
                .addPattern("[the] fake display text alignment of %displaydata/displaygroup%")          // 6
                .addPattern("[the] fake display line width of %displaydata/displaygroup%")              // 7
                .addPattern("[the] fake display brightness of %displaydata/displaygroup%")               // 8
                .addPattern("[the] fake display interpolation start [delay] of %displaydata/displaygroup%") // 9
                .addPattern("[the] fake display interpolation duration of %displaydata/displaygroup%")   // 10
                .addPattern("[the] fake display teleportation duration of %displaydata/displaygroup%")   // 11
                .addPattern("[the] fake display glow [color] of %displaydatas/displaygroups%")           // 12
                .addPattern("[the] fake display shadow radius of %displaydatas/displaygroups%")           // 13
                .addPattern("[the] fake display shadow strength of %displaydatas/displaygroups%")         // 14
                .addPattern("[the] fake display view range of %displaydatas/displaygroups%")              // 15
                .addPattern("[the] fake display billboard [mode] of %displaydatas/displaygroups%")        // 16
                .addPattern("[the] fake display display mode of %displaydatas/displaygroups%")            // 17
                .addPattern("[the] fake display text alignment of %displaydatas/displaygroups%")          // 18
                .addPattern("[the] fake display line width of %displaydatas/displaygroups%")              // 19
                .addPattern("[the] fake display brightness of %displaydatas/displaygroups%")               // 20
                .addPattern("[the] fake display interpolation start [delay] of %displaydatas/displaygroups%") // 21
                .addPattern("[the] fake display interpolation duration of %displaydatas/displaygroups%")   // 22
                .addPattern("[the] fake display teleportation duration of %displaydatas/displaygroups%")   // 23
                .build()
        );
    }
}
