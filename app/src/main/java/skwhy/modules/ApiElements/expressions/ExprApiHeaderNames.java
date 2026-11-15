package skwhy.modules.ApiElements.expressions;

import java.util.ArrayList;
import java.util.List;

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
import skwhy.apiclient.ApiCall;
import skwhy.apiclient.ApiResponse;

@Name("[API] API Header Names")
@Description("The names of all the headers of an api call or of an api response (lower case for a response). Read the value with 'api header \"name\" of'.")
@Examples({
    "loop api header names of {_response}:",
    "\tbroadcast \"%loop-value%: %api header loop-value of {_response}%\""
})
@Since("1.6.1")
public class ExprApiHeaderNames extends SimpleExpression<String> {

    private Expression<?> target;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprApiHeaderNames.class, String.class)
                .addPattern("api header names of %apicall/apiresponse%")
                .build()
        );
    }

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        target = exprs[0];
        return true;
    }

    @Override
    protected @Nullable String[] get(Event event) {
        Object source = target.getSingle(event);
        List<String> names = new ArrayList<>();
        if (source instanceof ApiResponse response) names.addAll(response.headerNames());
        else if (source instanceof ApiCall call) names.addAll(call.headers().keySet());
        return names.toArray(new String[0]);
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "api header names of " + target.toString(event, debug);
    }
}
