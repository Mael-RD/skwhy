package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.RequiredPlugins;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.util.Color;
import ch.njol.skript.util.ColorRGB;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.DisplayData;
import skwhy.data.TextDisplayData;

@Name("Display Background Color")
@Description("Gets or sets the background color of a text display entity using Skript's color type. " +
        "Alpha is preserved. Targets can be one or more displays or a display group.")
@Examples({
    "set {_display} to a new fake text display",
    "set fake display background color of {_display} to lime",
    "set {_color} to the fake display background color of {_display}"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class DisplayColor extends SimpleExpression<Color> {

    private Expression<?> targets;
    private boolean pluralTargets;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        pluralTargets = matchedPattern >= 1;
        targets = exprs[0];
        return true;
    }

    @Override
    protected @Nullable Color[] get(Event event) {
        if (pluralTargets) return null;
        Object value = targets.getSingle(event);
        return value instanceof TextDisplayData text
                ? new Color[]{ColorRGB.fromBukkitColor(text.getBackgroundColor())}
                : null;
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{Color.class};
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || !(delta[0] instanceof Color color)) return;

        for (DisplayData display : DisplayExpressionTargets.resolveForChange(targets, event, pluralTargets)) {
            if (display instanceof TextDisplayData text) {
                text.setBackgroundColor(color.asBukkitColor());
            }
        }
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Class<? extends Color> getReturnType() {
        return Color.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "background color of " + targets.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
                SyntaxRegistry.EXPRESSION,
                SyntaxInfo.Expression.builder(DisplayColor.class, Color.class)
                        .addPattern("[the] fake display background [color] of %displaydata/displaygroup%")
                        .addPattern("[the] fake display background [color] of %displaydatas/displaygroups%")
                        .build()
        );
    }
}
