package skwhy.request.acme;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/** Client minimal de l'API OVH pour poser et retirer l'enregistrement TXT du défi ACME DNS-01. */
public final class OvhDns {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final String base;
    private final String appKey;
    private final String appSecret;
    private final String consumerKey;
    private final String zone;
    private long timeDelta;
    private boolean synced;

    public OvhDns(String endpoint, String appKey, String appSecret, String consumerKey, String zone) {
        this.base = baseUrl(endpoint);
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.consumerKey = consumerKey;
        this.zone = zone;
    }

    /** {@code ovh-eu} (défaut), {@code ovh-ca}, {@code ovh-us}, ou une URL complète. */
    static String baseUrl(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || endpoint.equals("ovh-eu")) return "https://eu.api.ovh.com/1.0";
        return switch (endpoint) {
            case "ovh-ca" -> "https://ca.api.ovh.com/1.0";
            case "ovh-us" -> "https://api.us.ovhcloud.com/1.0";
            default -> endpoint.replaceAll("/+$", "");
        };
    }

    /** Signature OVH : {@code $1$} + SHA-1 hexadécimal de {@code secret+consumer+méthode+url+corps+horodatage}. */
    static String signature(String secret, String consumer, String method, String url, String body, long timestamp) {
        try {
            String raw = secret + "+" + consumer + "+" + method + "+" + url + "+" + body + "+" + timestamp;
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(raw.getBytes(StandardCharsets.UTF_8));
            return "$1$" + HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Crée un TXT {@code subDomain.zone} et retourne son id. */
    public long addTxt(String subDomain, String value) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("fieldType", "TXT");
        body.addProperty("subDomain", subDomain);
        body.addProperty("target", value);
        body.addProperty("ttl", 60);
        String answer = call("POST", "/domain/zone/" + zone + "/record", body.toString());
        return JsonParser.parseString(answer).getAsJsonObject().get("id").getAsLong();
    }

    public void removeRecord(long id) throws IOException, InterruptedException {
        call("DELETE", "/domain/zone/" + zone + "/record/" + id, "");
    }

    /** Publie la zone (les changements d'enregistrements n'y sont visibles qu'après). */
    public void refresh() throws IOException, InterruptedException {
        call("POST", "/domain/zone/" + zone + "/refresh", "");
    }

    private synchronized long now() throws IOException, InterruptedException {
        if (!synced) {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/auth/time")).timeout(Duration.ofSeconds(15)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) throw new IOException("OVH /auth/time : HTTP " + res.statusCode());
            timeDelta = Long.parseLong(res.body().trim()) - System.currentTimeMillis() / 1000;
            synced = true;
        }
        return System.currentTimeMillis() / 1000 + timeDelta;
    }

    private String call(String method, String path, String body) throws IOException, InterruptedException {
        String url = base + path;
        long ts = now();
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("X-Ovh-Application", appKey)
                .header("X-Ovh-Consumer", consumerKey)
                .header("X-Ovh-Timestamp", Long.toString(ts))
                .header("X-Ovh-Signature", signature(appSecret, consumerKey, method, url, body, ts));
        if (body.isEmpty()) {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("OVH " + method + " " + path + " : HTTP " + res.statusCode() + " " + res.body());
        }
        return res.body();
    }
}
