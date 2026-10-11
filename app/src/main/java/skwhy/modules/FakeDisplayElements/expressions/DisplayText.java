package skwhy.modules.FakeDisplayElements.expressions;

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
import skwhy.data.TextDisplayData;

@Name("Display Text Content")
@Description("Gets, sets, or appends to the text content of text displays. Targets can be one or more displays or a display group. Supports SET and ADD change modes. Colors use the Skript tags (<red>, <#ff8800>, <bold>...); the legacy &a codes still work.")
@Examples({
    "set {_group} to a new fake display group at player",
    "set {_display} to a new fake item display:",
    "    item: dirt",
    "set {_group} to a new fake display group at player from {_display}",
    "",
    "# Set the displayed text",
    "set fake display text of {_display} to \"<green>Hello world!\"",
    "set fake display text of {_group} to \"<green>Hello group!\"",
    "",
    "# Append text to the existing content",
    "add \" <aqua>(extra)\" to fake display text content of {_display}",
    "",
    "# Read the current text",
    "set {_text} to the fake display text of {_display}"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class DisplayText extends SimpleExpression<String> {

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
    protected @Nullable String[] get(Event event) {
        if (pluralTargets) return null;
        Object value = targets.getSingle(event);
        if (!(value instanceof TextDisplayData text) || text.getText() == null) return null;
        return new String[]{ text.getText() };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET || mode == ChangeMode.ADD) return new Class<?>[]{ String.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if ((mode == ChangeMode.SET || mode == ChangeMode.ADD)
                && (delta == null || !(delta[0] instanceof String))) return;
        String value = (mode == ChangeMode.SET || mode == ChangeMode.ADD) ? (String) delta[0] : null;
        for (DisplayData display : DisplayExpressionTargets.resolveForChange(targets, event, pluralTargets)) {
            if (!(display instanceof TextDisplayData text)) continue;
            switch (mode) {
                case SET -> text.setText(value);
                case ADD -> text.setText((text.getText() != null ? text.getText() : "") + value);
                case REMOVE, DELETE, RESET -> text.setText("");
                default -> {}
            }
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "text of " + targets.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(DisplayText.class, String.class)
                .addPattern("[the] fake display text [content] of %displaydata/displaygroup%")
                .addPattern("[the] fake display text [content] of %displaydatas/displaygroups%")
                .build()
        );
    }
}
