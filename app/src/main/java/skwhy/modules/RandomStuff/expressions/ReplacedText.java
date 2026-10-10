package skwhy.modules.RandomStuff.expressions;

import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Since;

import ch.njol.skript.Skript;
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

import skwhy.modules.RandomStuff.sections.ReplaceRegexSection;

@Name("Replaced Text")
@Description({
    "The text that will replace the current match at the end of the iteration of a 'replace regex ... with code' section.",
    "Defaults to the matched text. Can be set, deleted (removes the match) or reset (back to the matched text)."
})
@Examples({
    "replace regex \"[aeiou]\" in {_text} with code:",
    "\tset replaced text to \"*\"",
    "replace regex \"\\s+\" in {_text} with code:",
    "\tdelete replaced text"
})
@Since("1.4.0")
public class ReplacedText extends SimpleExpression<String> {

    private ReplaceRegexSection section;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        section = getParser().getCurrentSection(ReplaceRegexSection.class);
        if (section == null) {
            Skript.error("'replaced text' ne peut être utilisé que dans une section 'replace regex ... with code'.");
            return false;
        }
        return true;
    }

    @Override
    protected @Nullable String[] get(Event event) {
        ReplaceRegexSection.ReplaceState state = section.getState(event);
        if (state == null || state.getReplaced() == null) return new String[0];
        return new String[]{ state.getReplaced() };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case SET, DELETE, RESET -> new Class<?>[]{ String.class };
            default -> null;
        };
    }

    @Override
    public void change(Event event, @Nullable Object[] delta, ChangeMode mode) {
        ReplaceRegexSection.ReplaceState state = section.getState(event);
        if (state == null) return;

        switch (mode) {
            case SET -> {
                if (delta != null && delta.length > 0 && delta[0] != null) state.setReplaced(delta[0].toString());
            }
            case DELETE -> state.setReplaced("");
            case RESET -> state.setReplaced(state.getMatched());
            default -> {}
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "replaced text";
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ReplacedText.class, String.class)
                .addPattern("[the] replaced text")
                .build()
        );
    }
}
