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
@Description("Gets, sets, or appends to the text content of a text display entity. Only works on DisplayData instances of the TextDisplay type. Supports SET and ADD change modes.")
@Examples({
    "set {_group} to a new fake display group at player",
    "set {_display} to [a new fake item display]:",
    "    set item of display to dirt",
    "",
    "# Set the displayed text",
    "set text of {_display} to \"&aHello world!\"",
    "",
    "# Append text to the existing content",
    "add \" &b(extra)\" to text content of {_display}",
    "",
    "# Read the current text",
    "set {_text} to the text of {_display}"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class DisplayText extends SimpleExpression<String> {

    private Expression<DisplayData> displayExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        displayExpr = (Expression<DisplayData>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable String[] get(Event event) {
        DisplayData d = displayExpr.getSingle(event);
        if (!(d instanceof TextDisplayData text)) return null;
        String content = text.getText();
        return content != null ? new String[]{ content } : null;
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET || mode == ChangeMode.ADD) return new Class<?>[]{ String.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (delta == null || !(delta[0] instanceof String s)) return;
        DisplayData d = displayExpr.getSingle(event);
        if (!(d instanceof TextDisplayData text)) return;

        switch (mode) {
            case SET -> text.setText(s);
            // ADD permet de concaténer du texte existant
            case ADD -> text.setText((text.getText() != null ? text.getText() : "") + s);
            case REMOVE, DELETE, RESET -> text.setText("");
            default -> {}
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "text of " + displayExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(DisplayText.class, String.class)
                .addPattern("[the] text [content] of %displaydata%")
                .build()
        );
    }
}
