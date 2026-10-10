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

import java.util.regex.MatchResult;

@Name("Matched Group")
@Description("A capture group (by index or by name) of the current match in a 'replace regex ... with code' section. "
    + "Group 0 is the whole matched text. Cannot be changed.")
@Examples({
    "replace regex \"(\\w+)@(\\w+)\" in {_text} with code:",
    "\tset replaced text to \"%matched group 2%@%matched group 1%\"",
    "replace regex \"(?<key>\\w+)=(?<value>\\w+)\" in {_text} with code:",
    "\tset replaced text to matched group named \"value\""
})
@Since("1.4.0")
public class MatchedGroup extends SimpleExpression<String> {

    private ReplaceRegexSection section;
    private @Nullable Expression<Number> indexExpr;
    private @Nullable Expression<String> nameExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        section = getParser().getCurrentSection(ReplaceRegexSection.class);
        if (section == null) {
            Skript.error("'matched group' ne peut être utilisé que dans une section 'replace regex ... with code'.");
            return false;
        }
        if (matchedPattern == 0) indexExpr = (Expression<Number>) exprs[0];
        else nameExpr = (Expression<String>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable String[] get(Event event) {
        ReplaceRegexSection.ReplaceState state = section.getState(event);
        if (state == null) return new String[0];
        MatchResult match = state.getMatch();
        if (match == null) return new String[0];

        String group;
        try {
            if (indexExpr != null) {
                Number index = indexExpr.getSingle(event);
                if (index == null) return new String[0];
                group = match.group(index.intValue());
            } else {
                String name = nameExpr.getSingle(event);
                if (name == null) return new String[0];
                group = match.group(name);
            }
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            // Groupe inexistant
            return new String[0];
        }
        return group == null ? new String[0] : new String[]{ group };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return indexExpr != null
            ? "matched group " + indexExpr.toString(event, debug)
            : "matched group named " + nameExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(MatchedGroup.class, String.class)
                .addPattern("[the] matched group %number%")
                .addPattern("[the] matched group named %string%")
                .build()
        );
    }
}
