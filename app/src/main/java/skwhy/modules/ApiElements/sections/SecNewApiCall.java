package skwhy.modules.ApiElements.sections;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Section;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.util.Timespan;
import ch.njol.util.Kleenean;
import skwhy.SkWhy;
import skwhy.apiclient.ApiCall;
import skwhy.modules.ApiElements.ApiValueParser;

@Name("[API] New API Call With Options")
@Description({
    "Creates an outgoing HTTP or HTTPS request (an 'api call') and puts it in the variable. Each line is 'key: value' and the value is an ordinary Skript expression " +
        "(variables, functions, text with %...%). 'url' is required, the others are optional and can be in any order:",
    "url (text), method (GET by default, POST, PUT, PATCH, DELETE, HEAD, OPTIONS), timeout (time span), follow redirects (boolean, true by default), " +
        "trust all certificates (boolean, for a self-signed certificate; refused unless api_client.allow_insecure_certificates is true in the config).",
    "Authentication: bearer (the token, sent as 'Authorization: Bearer ...'), basic ('\"user\" and \"password\"', or one text 'user:password'), " +
        "api key ('\"header name\" and \"key\"', sent as a header).",
    "Content: body (raw text), json (text sent as the body with 'Content-Type: application/json'), content type (text). With neither body nor json, the 'form:' fields are sent as a form (application/x-www-form-urlencoded).",
    "Sub-sections of 'name: value' lines: headers, query (url parameters, encoded for you), form. A name with special characters can be quoted (\"X-API-Key\": ...). " +
        "A value that is a single word (GET, application/json) can be written without quotes.",
    "The variable stays unset when the url or the method is invalid (a warning is written in the console)."
})
@Examples({
    "set {_call} to a new api call:",
    "\turl: \"https://api.example.com/users\"",
    "\tmethod: POST",
    "\tbearer: {token}",
    "\ttimeout: 5 seconds",
    "\theaders:",
    "\t\tAccept: \"application/json\"",
    "\tquery:",
    "\t\tnotify: true",
    "\tjson: \"{\"\"name\"\": \"\"%player%\"\"}\"",
    "set {_form} to a new api call:",
    "\turl: \"https://auth.example.com/token\"",
    "\tmethod: POST",
    "\tbasic: \"my-client\" and \"my-secret\"",
    "\tform:",
    "\t\tgrant_type: \"client_credentials\""
})
@Since("1.6.1")
public class SecNewApiCall extends Section {

    private static final Set<String> KEYS = Set.of("url", "method", "timeout", "follow redirects", "trust all certificates",
        "bearer", "basic", "api key", "body", "json", "content type");
    private static final Set<String> MAPS = Set.of("headers", "query", "form");

    private Expression<?> resultVar;
    private final Map<String, Expression<?>> values = new LinkedHashMap<>();
    private final Map<String, Map<String, Expression<?>>> maps = new LinkedHashMap<>();

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.SECTION,
            SyntaxInfo.builder(SecNewApiCall.class)
                .addPattern("set %~objects% to [a|an] [new] api call")
                .build()
        );
    }

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult,
                        @Nullable SectionNode sectionNode, List<TriggerItem> triggerItems) {
        resultVar = exprs[0];
        if (sectionNode == null) {
            Skript.error("'new api call' needs a section ('url: ...', 'method: ...'): add ':' at the end of the line.");
            return false;
        }
        for (Node node : sectionNode) {
            String line = node.getKey();
            if (line == null || line.isBlank()) continue;
            if (node instanceof SectionNode sub) { // le nom d'une sous-section n'a pas son « : »
                String name = normalize(line);
                if (!MAPS.contains(name)) {
                    Skript.error("Unknown sub-section '" + line + "' (expected: " + String.join(", ", MAPS) + ").");
                    return false;
                }
                if (maps.containsKey(name)) {
                    Skript.error("'" + name + "' is written twice.");
                    return false;
                }
                Map<String, Expression<?>> entries = readEntries(name, sub);
                if (entries == null) return false;
                maps.put(name, entries);
                continue;
            }
            String[] split = splitKey(line);
            if (split == null) {
                Skript.error("Invalid line '" + line + "': 'key: value' expected.");
                return false;
            }
            String key = normalize(split[0]);
            if (!KEYS.contains(key)) {
                Skript.error("Unknown key '" + split[0] + "' (expected: " + String.join(", ", KEYS) + ", " + String.join(", ", MAPS) + ").");
                return false;
            }
            if (values.containsKey(key)) {
                Skript.error("'" + key + "' is written twice.");
                return false;
            }
            Expression<?> expr = parseKey(key, split[1]);
            if (expr == null) {
                Skript.error("Invalid value for '" + key + "': " + split[1]);
                return false;
            }
            values.put(key, expr);
        }
        if (!values.containsKey("url")) {
            Skript.error("'new api call' needs a 'url: ...' line.");
            return false;
        }
        if (values.containsKey("body") && values.containsKey("json")) {
            Skript.error("'body' and 'json' cannot be used together.");
            return false;
        }
        return true;
    }

    private @Nullable Map<String, Expression<?>> readEntries(String section, SectionNode sub) {
        Map<String, Expression<?>> entries = new LinkedHashMap<>();
        for (Node node : sub) {
            String line = node.getKey();
            if (line == null || line.isBlank()) continue;
            String[] split = splitKey(line);
            if (split == null || node instanceof SectionNode) {
                Skript.error("Invalid line '" + line + "' in '" + section + "': 'name: value' expected.");
                return null;
            }
            if (entries.containsKey(split[0])) {
                Skript.error("'" + split[0] + "' is written twice in '" + section + "'.");
                return null;
            }
            Expression<?> expr = ApiValueParser.parseText(split[1]);
            if (expr == null) {
                Skript.error("Invalid value for '" + split[0] + "' in '" + section + "': " + split[1]);
                return null;
            }
            entries.put(split[0], expr);
        }
        return entries;
    }

    private static @Nullable Expression<?> parseKey(String key, String value) {
        if (value.isEmpty()) return null;
        return switch (key) {
            case "timeout" -> ApiValueParser.parse(value, Timespan.class);
            case "follow redirects", "trust all certificates" -> ApiValueParser.parse(value, Boolean.class);
            default -> ApiValueParser.parseText(value);
        };
    }

    /** « nom: valeur » ou « "nom": valeur » (guillemets pour un nom qui contient des caractères spéciaux). */
    static String @Nullable [] splitKey(String line) {
        String text = line.trim();
        int colon;
        String name;
        if (text.startsWith("\"")) {
            int close = text.indexOf('"', 1);
            if (close < 0) return null;
            name = text.substring(1, close);
            colon = text.indexOf(':', close);
            if (colon < 0 || !text.substring(close + 1, colon).isBlank()) return null;
        } else {
            colon = text.indexOf(':');
            if (colon <= 0) return null;
            name = text.substring(0, colon).trim();
        }
        if (name.isEmpty()) return null;
        return new String[]{ name, text.substring(colon + 1).trim() };
    }

    private static String normalize(String key) {
        return key.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ");
    }

    @Override
    protected @Nullable TriggerItem walk(Event event) {
        ApiCall call = build(event);
        if (call != null) resultVar.change(event, new Object[]{ call }, ChangeMode.SET);
        return walk(event, false);
    }

    private @Nullable ApiCall build(Event event) {
        String url = ApiValueParser.text(values.get("url").getSingle(event));
        if (url == null) return null;
        ApiCall call = new ApiCall(url);
        try {
            String method = text("method", event);
            if (method != null) call.method(method);
        } catch (IllegalArgumentException e) {
            SkWhy.getInstance().getLogger().warning("[ApiClient] " + e.getMessage());
            return null;
        }
        if (values.get("timeout") != null && values.get("timeout").getSingle(event) instanceof Timespan span) {
            call.timeoutMillis(span.getAs(Timespan.TimePeriod.MILLISECOND));
        }
        if (values.get("follow redirects") != null && values.get("follow redirects").getSingle(event) instanceof Boolean b) call.followRedirects(b);
        if (values.get("trust all certificates") != null && values.get("trust all certificates").getSingle(event) instanceof Boolean b) call.trustAllCertificates(b);

        Map<String, String> headers = call.headers();
        evaluate("headers", event, headers);
        evaluate("query", event, call.query());
        evaluate("form", event, call.form());

        String contentType = text("content type", event);
        if (contentType != null) headers.put("Content-Type", contentType);
        String json = text("json", event);
        if (json != null) call.json(json);
        String body = text("body", event);
        if (body != null) call.body(body);

        String bearer = text("bearer", event);
        if (bearer != null) call.bearer(bearer);
        pair("basic", event, ":", call::basic);
        Expression<?> apiKey = values.get("api key");
        if (apiKey != null) {
            Object[] parts = apiKey.getArray(event);
            if (parts.length == 2) headers.put(ApiValueParser.text(parts[0]), ApiValueParser.text(parts[1]));
            else SkWhy.getInstance().getLogger().warning("[ApiClient] 'api key' expects two values: \"header name\" and \"key\".");
        }
        return call;
    }

    private @Nullable String text(String key, Event event) {
        Expression<?> expr = values.get(key);
        return expr == null ? null : ApiValueParser.text(expr.getSingle(event));
    }

    private void pair(String key, Event event, String separator, java.util.function.Consumer<String> setter) {
        Expression<?> expr = values.get(key);
        if (expr == null) return;
        Object[] parts = expr.getArray(event);
        if (parts.length == 2) setter.accept(ApiValueParser.text(parts[0]) + separator + ApiValueParser.text(parts[1]));
        else if (parts.length == 1) setter.accept(ApiValueParser.text(parts[0]));
        else SkWhy.getInstance().getLogger().warning("[ApiClient] '" + key + "' expects \"user\" and \"password\" (or one text 'user:password').");
    }

    private void evaluate(String section, Event event, Map<String, String> target) {
        Map<String, Expression<?>> entries = maps.get(section);
        if (entries == null) return;
        entries.forEach((name, expr) -> {
            String value = ApiValueParser.text(expr.getSingle(event));
            if (value != null) target.put(name, value);
        });
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "set " + resultVar.toString(event, debug) + " to a new api call";
    }
}
