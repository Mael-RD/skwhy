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
@Description("Splits a text into multiple parts using a regular expression (Regex).")
@Examples({
    "set {_parts::*} to \"apple,banana;orange\" split at regex \"[,;]\"",
    "broadcast \"%{_parts::1}%\""
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