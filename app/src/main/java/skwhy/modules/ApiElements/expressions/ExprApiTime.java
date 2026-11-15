package skwhy.modules.ApiElements.expressions;

import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.util.Timespan;
import ch.njol.util.Kleenean;
import skwhy.apiclient.ApiCall;
import skwhy.apiclient.ApiResponse;

@Name("[API] API Duration and Timeout")
@Description("'api duration of' is the time an api response took (read-only). " +
    "'api timeout of' is the maximum time a call may take, redirects included (set it, reset it to use api_client.default_timeout_seconds of the config). " +
    "When it is exceeded the response has the status 0 and the error 'timeout after N ms'.")
@Examples({
    "set api timeout of {_call} to 3 seconds",
    "send api call {_call} and wait for the response in {_response}",
    "broadcast \"answered in %api duration of {_response}%\""
})
@Since("1.6.1")
public class ExprApiTime extends SimpleExpression<Timespan> {

    private boolean timeout;
    private Expression<?> target;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprApiTime.class, Timespan.class)
                .addPattern("api duration of %apiresponse%")
                .addPattern("api timeout of %apicall%")
                .build()
        );
    }

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        timeout = matchedPattern == 1;
        target = exprs[0];
        return true;
    }

    @Override
    protected @Nullable Timespan[] get(Event event) {
        Object source = target.getSingle(event);
        Long millis = null;
        if (source instanceof ApiResponse response) millis = response.durationMillis();
        else if (source instanceof ApiCall call) millis = call.timeoutMillis();
        return millis == null ? null : new Timespan[]{ new Timespan(Timespan.TimePeriod.MILLISECOND, millis) };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (!timeout) return null;
        return switch (mode) {
            case SET -> new Class<?>[]{ Timespan.class };
            case DELETE, RESET -> new Class<?>[0];
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (!(target.getSingle(event) instanceof ApiCall call)) return;
        if (mode == ChangeMode.SET && delta != null && delta.length > 0 && delta[0] instanceof Timespan span) {
            call.timeoutMillis(span.getAs(Timespan.TimePeriod.MILLISECOND));
        } else {
            call.timeoutMillis(null);
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<Timespan> getReturnType() { return Timespan.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "api " + (timeout ? "timeout" : "duration") + " of " + target.toString(event, debug);
    }
}
