package skwhy.quest.editor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import skwhy.SkWhy;

/**
 * Routes de l'éditeur, toutes sous {@code /editor/<clef>/}. Sans clef valide la connexion est fermée sans réponse,
 * exactement comme l'API REST avec une mauvaise clef.
 */
final class EditorHttp implements HttpHandler {

    private static final int MAX_BODY_BYTES = 16 * 1024 * 1024;
    private static final Pattern STATIC_NAME = Pattern.compile("[a-z0-9][a-z0-9._-]*\\.(js|css|svg)");
    private static final Map<String, byte[]> RESOURCES = new ConcurrentHashMap<>();

    @Override
    public void handle(HttpExchange exchange) {
        try {
            route(exchange);
        } catch (Throwable t) {
            SkWhy.getInstance().getLogger().warning("[QuestEditor] Requête abandonnée : " + t);
            exchange.close();
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!method.equals("GET") && !method.equals("POST")) {
            exchange.close();
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String rest = path.length() > EditorService.PATH.length() ? path.substring(EditorService.PATH.length()) : "";
        if (!rest.startsWith("/")) {
            exchange.close();
            return;
        }
        String[] parts = rest.substring(1).split("/", 2);
        EditorSession session = EditorService.sessions().find(parts[0]);
        if (session == null) {
            exchange.close(); // aucune réponse, comme l'API avec une mauvaise clef
            return;
        }
        String sub = parts.length > 1 ? parts[1] : "";

        if (sub.isEmpty() || sub.equals("index.html")) {
            if (!method.equals("GET")) { exchange.close(); return; }
            String html = bundledIndex().replace("__BASE__", EditorService.PATH + "/" + parts[0] + "/");
            send(exchange, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8), true);
            return;
        }
        if (sub.startsWith("static/")) {
            String name = sub.substring("static/".length());
            if (!method.equals("GET") || !STATIC_NAME.matcher(name).matches()) { exchange.close(); return; }
            byte[] bytes = name.equals(BUNDLE) ? bundle() : resource(name);
            if (bytes.length == 0) { sendJson(exchange, 404, "{\"error\":\"not found\"}"); return; }
            exchange.getResponseHeaders().set("Cache-Control", "private, max-age=3600");
            send(exchange, 200, contentType(name), bytes, false);
            return;
        }
        if (!sub.startsWith("api/")) {
            exchange.close();
            return;
        }
        api(exchange, method, session, sub.substring("api/".length()));
    }

    private void api(HttpExchange exchange, String method, EditorSession session, String route) throws IOException {
        if (route.equals("status") && method.equals("GET")) {
            sendJson(exchange, 200, EditorJson.write(EditorService.status(session)));
        } else if (route.equals("catalog") && method.equals("GET")) {
            sendJson(exchange, 200, EditorService.data().catalogJson());
        } else if (route.startsWith("unlockers/") && method.equals("GET")) {
            String json = EditorService.data().unlockersJson(route.substring("unlockers/".length()));
            if (json == null) sendJson(exchange, 404, "{\"error\":\"not found\"}");
            else sendJson(exchange, 200, json);
        } else if ((route.startsWith("quest/") || route.startsWith("npc/")) && method.equals("GET")) {
            int slash = route.indexOf('/');
            String json = EditorService.data().entityJson(route.substring(0, slash), route.substring(slash + 1));
            if (json == null) sendJson(exchange, 404, "{\"error\":\"not found\"}");
            else sendJson(exchange, 200, json);
        } else if (route.equals("submit") && method.equals("POST")) {
            byte[] body;
            try (InputStream in = exchange.getRequestBody()) {
                body = in.readNBytes(MAX_BODY_BYTES + 1);
            }
            if (body.length > MAX_BODY_BYTES) {
                sendJson(exchange, 413, "{\"error\":\"too large\"}");
                return;
            }
            Object parsed;
            try {
                parsed = EditorJson.parse(new String(body, StandardCharsets.UTF_8));
            } catch (RuntimeException e) {
                sendJson(exchange, 400, "{\"ok\":false,\"error\":\"JSON invalide\"}");
                return;
            }
            EditorService.Response response = EditorService.submit(session, parsed);
            sendJson(exchange, response.status(), EditorJson.write(response.body()));
        } else {
            sendJson(exchange, 404, "{\"error\":\"not found\"}");
        }
    }

    // ── Réponses ────────────────────────────────────────────────────────────

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8), false);
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] bytes, boolean page) throws IOException {
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", type);
        if (!headers.containsKey("Cache-Control")) headers.set("Cache-Control", "no-store");
        headers.add("Vary", "Accept-Encoding");
        if (bytes.length > 1024 && acceptsGzip(exchange)) {
            bytes = gzip(bytes);
            headers.set("Content-Encoding", "gzip");
        }
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        if (page) {
            headers.set("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data:; connect-src 'self'; base-uri 'self'; form-action 'none'; frame-ancestors 'none'");
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static boolean acceptsGzip(HttpExchange exchange) {
        String accept = exchange.getRequestHeaders().getFirst("Accept-Encoding");
        return accept != null && accept.toLowerCase().contains("gzip");
    }

    private static byte[] gzip(byte[] bytes) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(bytes.length / 3 + 64);
        try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(out)) {
            gz.write(bytes);
        }
        return out.toByteArray();
    }

    // ── Un seul fichier JS : moins d'allers-retours (chacun coûte une connexion TLS à distance) ──────────────────

    private static final String BUNDLE = "bundle.js";
    private static final Pattern SCRIPT_TAG = Pattern.compile("<script src=\"static/([a-z0-9._-]+\\.js)\"></script>\\s*");
    private static volatile String bundledIndex;
    private static volatile byte[] bundleBytes;

    /** index.html dont les balises {@code <script src="static/x.js">} sont remplacées par une seule vers {@code bundle.js}. */
    private static String bundledIndex() {
        String cached = bundledIndex;
        if (cached != null) return cached;
        String html = new String(resource("index.html"), StandardCharsets.UTF_8);
        java.util.regex.Matcher m = SCRIPT_TAG.matcher(html);
        StringBuilder all = new StringBuilder();
        StringBuilder out = new StringBuilder();
        int last = 0;
        boolean first = true;
        while (m.find()) {
            all.append(new String(resource(m.group(1)), StandardCharsets.UTF_8)).append("\n;\n");
            out.append(html, last, m.start());
            if (first) out.append("<script src=\"static/").append(BUNDLE).append("\"></script>\n");
            first = false;
            last = m.end();
        }
        out.append(html.substring(last));
        bundleBytes = all.toString().getBytes(StandardCharsets.UTF_8);
        bundledIndex = out.toString();
        return bundledIndex;
    }

    private static byte[] bundle() {
        bundledIndex();
        return bundleBytes;
    }

    private static String contentType(String name) {
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        return "image/svg+xml";
    }

    /** Fichier du dossier {@code editor/} du jar (vide s'il n'existe pas). */
    private static byte[] resource(String name) {
        return RESOURCES.computeIfAbsent(name, n -> {
            try (InputStream in = EditorHttp.class.getResourceAsStream("/editor/" + n)) {
                return in == null ? new byte[0] : in.readAllBytes();
            } catch (IOException e) {
                return new byte[0];
            }
        });
    }
}
