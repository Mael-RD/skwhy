package skwhy.apiclient;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Réponse d'une requête sortante. {@code status} vaut 0 quand aucune réponse HTTP n'est arrivée
 * (délai dépassé, connexion refusée...) : {@code error} contient alors le motif.
 * Une réponse HTTP d'erreur (404, 500...) garde son vrai code et {@code error == null}.
 */
public final class ApiResponse {

    private final int status;
    private final String body;
    private final Map<String, List<String>> headers;
    private final long durationMillis;
    private final String error;
    private final String url;

    public ApiResponse(int status, String body, Map<String, List<String>> headers, long durationMillis, String error, String url) {
        this.status = status;
        this.body = body == null ? "" : body;
        this.headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) this.headers.putAll(headers);
        this.durationMillis = durationMillis;
        this.error = error;
        this.url = url == null ? "" : url;
    }

    public static ApiResponse failure(String error, long durationMillis, String url) {
        return new ApiResponse(0, "", Map.of(), durationMillis, error, url);
    }

    public int status() { return status; }
    public String body() { return body; }
    public long durationMillis() { return durationMillis; }
    /** Motif de l'échec réseau, {@code null} si une réponse HTTP a été reçue. */
    public String error() { return error; }
    /** URL finale (après les redirections). */
    public String url() { return url; }

    /** Valeurs d'un en-tête réunies par « , » ({@code null} si absent). */
    public String header(String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : String.join(", ", values);
    }

    public List<String> headerNames() {
        return List.copyOf(headers.keySet());
    }

    /** Vrai pour une réponse reçue avec un code 2xx. */
    public boolean isSuccessful() {
        return error == null && status >= 200 && status < 300;
    }

    @Override
    public String toString() {
        return error != null ? "api response (" + error + ")" : "api response " + status;
    }
}
