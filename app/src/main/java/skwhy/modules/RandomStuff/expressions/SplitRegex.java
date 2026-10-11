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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Name("Split with Regex")
@Description({
    "Splits a text around every match of a regular expression (Java regex syntax) and returns the result as a list.",
    "Unlike a classic split, the list KEEPS the matched separators: it alternates the text found before each match and the match itself, "
        + "and always ends with the text left after the last match: [text 1, match 1, text 2, match 2, ..., last text].",
    "So with n matches the list has 2n + 1 elements; odd indexes (1, 3, 5...) are the text parts and even indexes (2, 4, 6...) are the separators. "
        + "A text part is an empty text when two matches are adjacent or when the text starts/ends with a match.",
    "When the regex matches nothing the list only contains the whole text. An invalid regex gives a Skript warning and no value."
})
@Examples({
    "# \"apple,banana;orange\" -> apple , banana ; orange",
    "set {_parts::*} to split \"apple,banana;orange\" at regex \"[,;]\"",
    "broadcast \"%{_parts::1}%\" # apple",
    "broadcast \"%{_parts::2}%\" # , (the separator)",
    "broadcast \"%{_parts::3}%\" # banana",
    "",
    "# keep only the text parts (odd indexes)",
    "loop {_parts::*}:",
    "\tif mod(loop-index parsed as integer, 2) is 1:",
    "\t\tbroadcast loop-value",
    "",
    "# no match: the list only contains the whole text",
    "set {_parts::*} to split \"abc\" at regex \"\\d\"",
    "# {_parts::*} = \"abc\""
})
@Since("1.0.0")
public class SplitRegex extends SimpleExpression<String> {

    private Expression<String> inputExpr;
    private Expression<String> regexExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        inputExpr = (Expression<String>) exprs[0];
        regexExpr = (Expression<String>) exprs[1];
        return true;
    }

    @Override
    protected @Nullable String[] get(Event event) {
        String input = inputExpr.getSingle(event);
        String regex = regexExpr.getSingle(event);

        if (input == null || regex == null) return null;

        Pattern pattern;
        try {
            // ^ et $ s'ancrent au vrai début/fin du string (comportement Java par défaut)
            pattern = Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            Skript.warning("Regex invalide : " + e.getMessage());
            return null;
        }

        Matcher matcher = pattern.matcher(input);
        List<String> result = new ArrayList<>();
        int lastEnd = 0;

        while (matcher.find()) {
            // Partie texte avant ce match (peut être vide)
            result.add(input.substring(lastEnd, matcher.start()));
            // Partie détectée par le regex
            result.add(matcher.group());
            lastEnd = matcher.end();

            // Évite les boucles infinies sur les regex qui matchent une chaîne vide
            if (matcher.start() == matcher.end()) {
                if (matcher.end() < input.length()) lastEnd++;
                else break;
            }
        }

        // Partie texte finale (peut être vide)
        result.add(input.substring(lastEnd));

        return result.toArray(new String[0]);
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "split " + inputExpr.toString(event, debug)
            + " at regex " + regexExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(SplitRegex.class, String.class)
                .addPattern("split %string% at regex %string%")
                .build()
        );
    }
}