package skwhy.apiclient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Requête sortante en cours de construction (type Skript {@code apicall}). */
public final class ApiCall {

    public static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    private String url;
    private String method = "GET";
    private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, String> query = new LinkedHashMap<>();
    private final Map<String, String> form = new LinkedHashMap<>();
    private String body;
    private Long timeoutMillis;
    private boolean followRedirects = true;
    private boolean trustAllCertificates;
    private String bearer;
    private String basic;

    public ApiCall(String url) {
        this.url = url == null ? "" : url.trim();
    }

    public String url() { return url; }
    public void url(String url) { this.url = url == null ? "" : url.trim(); }

    public String method() { return method; }

    /** @throws IllegalArgumentException si la méthode n'est pas GET/POST/PUT/PATCH/DELETE/HEAD/OPTIONS */
    public void method(String method) {
        String upper = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(upper)) {
            throw new IllegalArgumentException("unknown method '" + method + "' (expected " + String.join(", ", new TreeSet<>(METHODS)) + ")");
        }
        this.method = upper;
    }

    public Map<String, String> headers() { return headers; }
    public Map<String, String> query() { return query; }
    public Map<String, String> form() { return form; }

    public String body() { return body; }
    public void body(String body) { this.body = body; }

    /** Corps JSON : fixe aussi {@code Content-Type} (sauf s'il est déjà défini). */
    public void json(String json) {
        this.body = json;
        headers.putIfAbsent("Content-Type", "application/json");
    }

    public Long timeoutMillis() { return timeoutMillis; }
    public void timeoutMillis(Long timeoutMillis) { this.timeoutMillis = timeoutMillis == null || timeoutMillis <= 0 ? null : timeoutMillis; }

    public boolean followRedirects() { return followRedirects; }
    public void followRedirects(boolean followRedirects) { this.followRedirects = followRedirects; }

    public boolean trustAllCertificates() { return trustAllCertificates; }
    public void trustAllCertificates(boolean trustAllCertificates) { this.trustAllCertificates = trustAllCertificates; }

    public String bearer() { return bearer; }
    public void bearer(String token) { this.bearer = token == null || token.isEmpty() ? null : token; }

    /** « utilisateur:mot de passe » (Basic), {@code null} si absent. */
    public String basic() { return basic; }
    public void basic(String userAndPassword) { this.basic = userAndPassword == null || userAndPassword.isEmpty() ? null : userAndPassword; }

    /** URL complète : les paramètres de {@link #query()} sont encodés et ajoutés à la requête déjà présente dans l'URL. */
    public URI buildUri() {
        String base = url;
        if (base.isEmpty()) throw new IllegalArgumentException("the url is empty");
        String fragment = "";
        int hash = base.indexOf('#');
        if (hash >= 0) {
            fragment = base.substring(hash);
            base = base.substring(0, hash);
        }
        if (!query.isEmpty()) {
            base += (base.contains("?") ? (base.endsWith("?") || base.endsWith("&") ? "" : "&") : "?") + encode(query);
        }
        try {
            return URI.create(base + fragment);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid url: " + e.getMessage());
        }
    }

    /** Corps envoyé : le texte fixé, sinon le formulaire encodé, sinon {@code null}. */
    public String effectiveBody() {
        if (body != null) return body;
        return form.isEmpty() ? null : encode(form);
    }

    /** En-têtes envoyés : ceux fixés + Content-Type du formulaire + Authorization (bearer / basic) si absent. */
    public Map<String, String> effectiveHeaders() {
        Map<String, String> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        out.putAll(headers);
        if (body == null && !form.isEmpty()) out.putIfAbsent("Content-Type", "application/x-www-form-urlencoded");
        if (!out.containsKey("Authorization")) {
            if (bearer != null) out.put("Authorization", "Bearer " + bearer);
            else if (basic != null) out.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8)));
        }
        return out;
    }

    /** Copie indépendante : modifier la requête après l'envoi n'affecte pas l'envoi en cours. */
    public ApiCall copy() {
        ApiCall c = new ApiCall(url);
        c.method = method;
        c.headers.putAll(headers);
        c.query.putAll(query);
        c.form.putAll(form);
        c.body = body;
        c.timeoutMillis = timeoutMillis;
        c.followRedirects = followRedirects;
        c.trustAllCertificates = trustAllCertificates;
        c.bearer = bearer;
        c.basic = basic;
        return c;
    }

    static String encode(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8).replace("+", "%20"))
              .append('=')
              .append(URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8).replace("+", "%20"));
        });
        return sb.toString();
    }

    @Override
    public String toString() {
        return "api call " + method + " " + url;
    }
}
