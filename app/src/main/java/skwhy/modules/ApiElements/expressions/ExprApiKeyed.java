package skwhy.modules.ApiElements.expressions;

import java.util.Map;

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
import ch.njol.skript.registrations.Classes;
import ch.njol.util.Kleenean;
import skwhy.apiclient.ApiCall;
import skwhy.apiclient.ApiResponse;

@Name("[API] API Header, Query Parameter and Form Field")
@Description({
    "A named value of an api call or an api response.",
    "'api header \"name\" of' reads the headers of a call or of a response (names are not case sensitive; several values of a response header are joined with \", \"). " +
        "On a call it can be set or deleted: use it for an API key header ('X-API-Key') or 'Content-Type'.",
    "'api query parameter \"name\" of' is a parameter of the url (?name=value); it is encoded for you and added to the query already present in the url.",
    "'api form field \"name\" of' is a field of an application/x-www-form-urlencoded body; it is used when no 'api body' is set.",
    "A number or a boolean is converted to text."
})
@Examples({
    "set api header \"X-API-Key\" of {_call} to {api_key}",
    "set api query parameter \"page\" of {_call} to 2",
    "set api form field \"grant_type\" of {_call} to \"client_credentials\"",
    "delete api header \"Accept\" of {_call}",
    "set {_type} to api header \"Content-Type\" of {_response}"
})
@Since("1.6.1")
public class ExprApiKeyed extends SimpleExpression<String> {

    private static final String[] NAMES = { "header", "query parameter", "form field" };

    private int kind;
    private Expression<String> nameExpr;
    private Expression<?> target;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprApiKeyed.class, String.class)
                .addPattern("api header %string% of %apicall/apiresponse%")
                .addPattern("api query parameter %string% of %apicall%")
                .addPattern("api form field %string% of %apicall%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        kind = matchedPattern;
        nameExpr = (Expression<String>) exprs[0];
        target = exprs[1];
        return true;
    }

    @Override
    protected @Nullable String[] get(Event event) {
        String name = nameExpr.getSingle(event);
        Object source = target.getSingle(event);
        if (name == null || source == null) return null;
        String value = null;
        if (source instanceof ApiResponse response) {
            if (kind == 0) value = response.header(name);
        } else if (source instanceof ApiCall call) {
            value = map(call).get(name);
        }
        return value == null ? null : new String[]{ value };
    }

    private Map<String, String> map(ApiCall call) {
        return switch (kind) {
            case 0 -> call.headers();
            case 1 -> call.query();
            default -> call.form();
        };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case SET -> new Class<?>[]{ Object.class };
            case DELETE, RESET -> new Class<?>[0];
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        String name = nameExpr.getSingle(event);
        if (name == null || !(target.getSingle(event) instanceof ApiCall call)) return; // une réponse ne se modifie pas
        Map<String, String> map = map(call);
        if (mode == ChangeMode.SET && delta != null && delta.length > 0 && delta[0] != null) {
            map.put(name, delta[0] instanceof String s ? s : Classes.toString(delta[0]));
        } else {
            map.remove(name);
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "api " + NAMES[kind] + " " + nameExpr.toString(event, debug) + " of " + target.toString(event, debug);
    }
}
