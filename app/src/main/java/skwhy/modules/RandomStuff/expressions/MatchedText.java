package skwhy.modules.RandomStuff.expressions;

import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Since;

import ch.njol.skript.Skript;
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

@Name("Matched Text")
@Description("The text matched by the regex in the current iteration of a 'replace regex ... with code' section. Cannot be changed.")
@Examples({
    "replace regex \"\\d+\" in {_text} with code:",
    "\tbroadcast \"found number: %matched text%\""
})
@Since("1.4.0")
public class MatchedText extends SimpleExpression<String> {

    private ReplaceRegexSection section;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        section = getParser().getCurrentSection(ReplaceRegexSection.class);
        if (section == null) {
            Skript.error("'matched text' ne peut être utilisé que dans une section 'replace regex ... with code'.");
            return false;
        }
        return true;
    }

    @Override
    protected @Nullable String[] get(Event event) {
        ReplaceRegexSection.ReplaceState state = section.getState(event);
        if (state == null || state.getMatched() == null) return new String[0];
        return new String[]{ state.getMatched() };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "matched text";
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(MatchedText.class, String.class)
                .addPattern("[the] matched text")
                .build()
        );
    }
}
