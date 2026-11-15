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
import skwhy.apiclient.ApiResponse;

@Name("[API] API Response Status")
@Description("HTTP status code of an api response (200, 201, 404, 500...). An HTTP error keeps its own code. " +
    "0 means that no HTTP answer arrived (timeout, refused connection, invalid certificate...): 'api error of' then gives the reason.")
@Examples({
    "send api call {_call} and wait for the response in {_response}",
    "if api status of {_response} is 404:",
    "\tbroadcast \"not found\"",
    "else if api status of {_response} is 0:",
    "\tbroadcast \"no answer: %api error of {_response}%\""
})
@Since("1.6.1")
public class ExprApiStatus extends SimpleExpression<Number> {

    private Expression<ApiResponse> responseExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprApiStatus.class, Number.class)
                .addPattern("api status [code] of %apiresponse%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        responseExpr = (Expression<ApiResponse>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Number[] get(Event event) {
        ApiResponse response = responseExpr.getSingle(event);
        return response == null ? null : new Number[]{ response.status() };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<Number> getReturnType() { return Number.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "api status of " + responseExpr.toString(event, debug);
    }
}
