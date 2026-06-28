package skwhy.modules.API.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.modules.API.types.API_request;

@Name("[API] Request Content")
@Description("Gets the method, path, query string, or body of an API request.")
@Examples({
    "on api request:",
    "\tset {_req} to event-apirequest",
    "\tset {_method} to method of {_req}",
    "\tset {_path}   to path of {_req}",
    "\tset {_query}  to query of {_req}",
    "\tset {_body}   to body of {_req}",
    "\tif {_method} is \"GET\":",
    "\t\treply to {_req} with \"{\\\"ok\\\": true}\""
})
@Since("1.3.0")
public class RequestContent extends SimpleExpression<String> {

    private int matchedPattern;
    private Expression<API_request> requestExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.matchedPattern = matchedPattern;
        this.requestExpr    = (Expression<API_request>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable String[] get(Event event) {
        API_request req = requestExpr.getSingle(event);
        if (req == null) return null;

        String value = switch (matchedPattern) {
            case 0 -> req.getMethod();
            case 1 -> req.getPath();
            case 2 -> req.getQuery();
            case 3 -> req.getBody();
            default -> null;
        };
        return value != null ? new String[]{ value } : null;
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return switch (matchedPattern) {
            case 0 -> "method of " + requestExpr.toString(event, debug);
            case 1 -> "path of "   + requestExpr.toString(event, debug);
            case 2 -> "query of "  + requestExpr.toString(event, debug);
            case 3 -> "body of "   + requestExpr.toString(event, debug);
            default -> "api request content";
        };
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(RequestContent.class, String.class)
                .addPattern("method of %apirequest%")
                .addPattern("path of %apirequest%")
                .addPattern("query of %apirequest%")
                .addPattern("body of %apirequest%")
                .build()
        );
    }
}