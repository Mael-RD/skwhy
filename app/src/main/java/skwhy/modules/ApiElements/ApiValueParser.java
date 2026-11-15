package skwhy.modules.ApiElements;

import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleLiteral;
import ch.njol.skript.log.ParseLogHandler;
import ch.njol.skript.log.SkriptLogger;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.util.LiteralUtils;

/** Lecture des valeurs d'une section « key: value » : chaque valeur est une expression Skript ordinaire. */
public final class ApiValueParser {

    /** Un mot sans espace ni guillemet (GET, application/json...) est pris tel quel comme texte. */
    private static final Pattern BARE_WORD = Pattern.compile("[A-Za-z0-9_:./+*;=,-]+");

    private ApiValueParser() {}

    /** Expression du type demandé, ou {@code null} (l'erreur de Skript est alors affichée). */
    public static @Nullable Expression<?> parse(String value, Class<?> type) {
        ParseLogHandler log = SkriptLogger.startParseLogHandler();
        try {
            Expression<?> parsed = new SkriptParser(value, SkriptParser.PARSE_EXPRESSIONS | SkriptParser.PARSE_LITERALS).parseExpression(type);
            if (parsed != null) {
                log.printLog();
                return parsed;
            }
            log.clear();
            return null;
        } finally {
            log.stop();
        }
    }

    /**
     * Valeur quelconque (texte, nombre, booléen, variable, liste) ; à défaut un mot seul sans guillemets.
     * Le type n'est pas imposé à l'analyse : une variable contenant un nombre reste un nombre, {@link #text(Object)} le convertit à l'exécution.
     */
    public static @Nullable Expression<?> parseText(String value) {
        if (value.isEmpty()) return null;
        ParseLogHandler log = SkriptLogger.startParseLogHandler();
        try {
            Expression<?> parsed = new SkriptParser(value, SkriptParser.PARSE_EXPRESSIONS | SkriptParser.PARSE_LITERALS).parseExpression(Object.class);
            if (parsed != null) {
                parsed = LiteralUtils.defendExpression(parsed);
                if (LiteralUtils.canInitSafely(parsed)) {
                    log.printLog();
                    return parsed;
                }
            }
            log.clear();
        } finally {
            log.stop();
        }
        if (BARE_WORD.matcher(value).matches()) return new SimpleLiteral<>(value, false);
        return null;
    }

    /** Texte d'une valeur évaluée (nombre et booléen convertis), {@code null} si non défini. */
    public static @Nullable String text(@Nullable Object value) {
        if (value == null) return null;
        return value instanceof String s ? s : Classes.toString(value);
    }
}
