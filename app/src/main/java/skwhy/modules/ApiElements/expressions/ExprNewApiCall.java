package skwhy.modules.ApiElements.expressions;

import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import skwhy.SkWhy;
import skwhy.apiclient.ApiCall;

@Name("[API] New API Call")
@Description("Creates an outgoing HTTP or HTTPS request (an 'api call') to send to any REST API. The method is GET by default " +
    "(GET, POST, PUT, PATCH, DELETE, HEAD or OPTIONS, in any case); an unknown method is refused with a warning and nothing is created. " +
    "The call is only built here: add headers, parameters, a body or an authentication with the 'api ... of' expressions " +
    "(or use the 'new api call' section to write everything at once), then send it with 'send api call'.")
@Examples({
    "set {_call} to a new api call to \"https://api.example.com/status\"",
    "set {_post} to a new api call to \"https://api.example.com/users\" with method \"POST\"",
    "set api body of {_post} to \"{\"\"name\"\": \"\"Bob\"\"}\"",
    "set api header \"Content-Type\" of {_post} to \"application/json\""
})
@Since("1.6.1")
public class ExprNewApiCall extends SimpleExpression<ApiCall> {

    private Expression<String> urlExpr;
    private @Nullable Expression<String> methodExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprNewApiCall.class, ApiCall.class)
                .addPattern("[a|an] [new] api call (to|at) %string% [with method %-string%]")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        urlExpr = (Expression<String>) exprs[0];
        methodExpr = (Expression<String>) exprs[1];
        return true;
    }

    @Override
    protected @Nullable ApiCall[] get(Event event) {
        String url = urlExpr.getSingle(event);
        if (url == null) return null;
        ApiCall call = new ApiCall(url);
        String method = methodExpr == null ? null : methodExpr.getSingle(event);
        if (method != null) {
            try {
                call.method(method);
            } catch (IllegalArgumentException e) {
                SkWhy.getInstance().getLogger().warning("[ApiClient] " + e.getMessage());
                return null;
            }
        }
        return new ApiCall[]{ call };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<ApiCall> getReturnType() { return ApiCall.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "a new api call to " + urlExpr.toString(event, debug) + (methodExpr == null ? "" : " with method " + methodExpr.toString(event, debug));
    }
}
