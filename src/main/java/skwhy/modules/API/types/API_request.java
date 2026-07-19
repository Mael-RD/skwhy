package skwhy.modules.API.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.classes.Serializer;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.yggdrasil.Fields;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
import java.nio.charset.StandardCharsets;

public class API_request {

    // ── Données de la requête ──
    private final HttpExchange exchange;
    private final String method;
    private final String path;
    private final String query;
    private final String body;
    private boolean replied = false;

    public API_request(HttpExchange exchange, String method, String path, String query, String body) {
        this.exchange = exchange;
        this.method   = method;
        this.path     = path;
        this.query    = query  != null ? query  : "";
        this.body     = body   != null ? body   : "";
    }

    // ── Getters accessibles depuis les expressions Skript ──

    public String getMethod() { return method; }
    public String getPath()   { return path;   }
    public String getQuery()  { return query;  }
    public String getBody()   { return body;   }

    /**
     * Envoie une réponse HTTP 200 avec le texte fourni.
     * Appelé depuis une expression Skript "reply to {_req} with ..."
     */
    public void reply(String responseBody) {
        reply(200, responseBody);
    }

    /**
     * Envoie une réponse HTTP avec un code et un corps personnalisés.
     */
    public void reply(int statusCode, String responseBody) {
        if (replied) return;
        replied = true;
        try {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            // Connexion déjà fermée côté client, on ignore
        } finally {
            exchange.close();
        }
    }

    /**
     * Ferme la connexion sans réponse si personne n'a répondu dans l'event.
     * Appelé automatiquement par ApiServer après le callEvent().
     */
    public void closeIfNotReplied() {
        if (!replied) {
            replied = true;
            exchange.close();
        }
    }

    @Override
    public String toString() {
        return "API_request[" + method + " " + path + "]";
    }

    // =========================================================================
    // Enregistrement du type Skript
    // =========================================================================

    public static void register() {
        Classes.registerClass(new ClassInfo<>(API_request.class, "apirequest")
            .name("API Request")
            .description(
                "Represents an incoming HTTP or HTTPS request received by the built-in API server. " +
                "Exposes the method, path, query string, and body. " +
                "Use 'reply to %apirequest% with %string%' to send a response."
            )
            .usage("Obtained via 'event-apirequest' inside an 'on api request' event.")
            .user("api ?requests?")
            .examples(
                "on api request:",
                "\tset {_req} to event-apirequest",
                "\tif method of {_req} is \"GET\":",
                "\t\treply to {_req} with \"{\\\"ok\\\": true}\"",
                "\telse:",
                "\t\treply to {_req} with code 405 and \"{\\\"error\\\": \\\"method not allowed\\\"}\""
            )
            .since("1.3.0")

            .parser(new Parser<>() {
                @Override
                public API_request parse(String s, ParseContext context) {
                    return null; // non parsable depuis du texte
                }

                @Override
                public boolean canParse(ParseContext context) {
                    return false;
                }

                @Override
                public String toString(API_request req, int flags) {
                    return req.toString();
                }

                @Override
                public String toVariableNameString(API_request req) {
                    return req.toString();
                }
            })

            .serializer(new Serializer<>() {
                @Override
                public Fields serialize(API_request req) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void deserialize(API_request req, Fields f) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public API_request deserialize(Fields fields) throws StreamCorruptedException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean mustSyncDeserialization() {
                    return false;
                }

                @Override
                protected boolean canBeInstantiated() {
                    return false;
                }
            })
        );
    }
}