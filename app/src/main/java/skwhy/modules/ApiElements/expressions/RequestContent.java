package skwhy.modules.ApiElements.expressions;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import skwhy.SkWhy;
import skwhy.apiclient.ApiCall;
import skwhy.apiclient.ApiResponse;
import skwhy.modules.ApiElements.types.API_request;

import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("[API] Request, Call and Response Text")
@Description({
    "Text values of the module's API objects, all written with the 'api' prefix.",
    "A request received by the API server ('api request' event): 'api method of', 'api path of', 'api query of', 'api body of'.",
    "A call to send ('api call'): 'api method of', 'api body of', 'api url of' can be set, 'api body of', 'api bearer token of' and 'api basic auth of' (\"user:password\") can also be deleted.",
    "A response received ('api response'): 'api body of', 'api url of' (final url after redirects) and 'api error of' (why no HTTP answer arrived: timeout, refused connection...; not set when an answer arrived, even a 404)."
})
@Examples({
    "on api request:",
    "\tset {_req} to event-apirequest",
    "\tset {_method} to api method of {_req}",
    "\tset {_path}   to api path of {_req}",
    "\tset {_query}  to api query of {_req}",
    "\tset {_body}   to api body of {_req}",
    "\tif {_method} is \"GET\":",
    "\t\tapi reply to {_req} with \"{\\\"ok\\\": true}\"",
    "",
    "set {_call} to a new api call to \"https://api.example.com/users\"",
    "set api method of {_call} to \"POST\"",
    "set api body of {_call} to \"{\"\"name\"\": \"\"Bob\"\"}\"",
    "set api bearer token of {_call} to \"my-secret-token\""
})
@Since("1.3.0 (the 'api' prefix and the call / response forms: 1.6.1)")
public class RequestContent extends SimpleExpression<String> {

    private static final String[] NAMES = {
        "method", "path", "query", "body", "url", "error", "bearer token", "basic auth"
    };

    private int matchedPattern;
    private Expression<?> target;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.matchedPattern = matchedPattern;
        this.target         = exprs[0];
        return true;
    }

    @Override
    protected @Nullable String[] get(Event event) {
        Object source = target.getSingle(event);
        if (source == null) return null;

        String value = null;
        if (source instanceof API_request req) {
            value = switch (matchedPattern) {
                case 0 -> req.getMethod();
                case 1 -> req.getPath();
                case 2 -> req.getQuery();
                case 3 -> req.getBody();
                default -> null;
            };
        } else if (source instanceof ApiCall call) {
            value = switch (matchedPattern) {
                case 0 -> call.method();
                case 3 -> call.body();
                case 4 -> call.url();
                case 6 -> call.bearer();
                case 7 -> call.basic();
                default -> null;
            };
        } else if (source instanceof ApiResponse response) {
            value = switch (matchedPattern) {
                case 3 -> response.body();
                case 4 -> response.url();
                case 5 -> response.error();
                default -> null;
            };
        }
        return value != null ? new String[]{ value } : null;
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        boolean settable = matchedPattern == 0 || matchedPattern == 3 || matchedPattern == 4 || matchedPattern == 6 || matchedPattern == 7;
        boolean clearable = matchedPattern == 3 || matchedPattern == 6 || matchedPattern == 7;
        return switch (mode) {
            case SET -> settable ? new Class<?>[]{ String.class } : null;
            case DELETE, RESET -> clearable ? new Class<?>[0] : null;
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (!(target.getSingle(event) instanceof ApiCall call)) return; // une requête reçue ou une réponse ne se modifie pas
        String value = mode == ChangeMode.SET && delta != null && delta.length > 0 && delta[0] != null ? delta[0].toString() : null;
        switch (matchedPattern) {
            case 0 -> {
                if (value == null) return;
                try {
                    call.method(value);
                } catch (IllegalArgumentException e) {
                    SkWhy.getInstance().getLogger().warning("[ApiClient] " + e.getMessage());
                }
            }
            case 3 -> call.body(value);
            case 4 -> {
                if (value != null) call.url(value);
            }
            case 6 -> call.bearer(value);
            case 7 -> call.basic(value);
            default -> { }
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "api " + NAMES[matchedPattern] + " of " + target.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(RequestContent.class, String.class)
                // tried before other addons' 'path of %x%' / 'body of %x%' when the variable type is unknown at parse time
                .priority(SyntaxInfo.SIMPLE)
                .addPattern("api method of %apirequest/apicall%")
                .addPattern("api path of %apirequest%")
                .addPattern("api query of %apirequest%")
                .addPattern("api body of %apirequest/apicall/apiresponse%")
                .addPattern("api url of %apicall/apiresponse%")
                .addPattern("api error of %apiresponse%")
                .addPattern("api bearer token of %apicall%")
                .addPattern("api basic auth of %apicall%")
                .build()
        );
    }
}
