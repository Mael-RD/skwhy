package skwhy.request;

import com.sun.net.httpserver.HttpExchange;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import skwhy.SkWhy;
import skwhy.modules.ApiElements.events.API_request_event;
import skwhy.modules.ApiElements.types.API_request;

import java.io.InputStream;
import java.util.logging.Logger;

/** API REST : transforme les requêtes reçues sur {@code /} en events Skript. Le serveur HTTP est celui de {@link HttpHost}. */
public class APIserver {

    /** Taille maximale acceptée pour le corps d'une requête. */
    private static final int MAX_BODY_BYTES = 1024 * 1024;

    private boolean started;
    private final Logger log = SkWhy.getInstance().getLogger();

    public boolean isStarted() {
        return started;
    }

    public boolean start() {
        FileConfiguration cfg = SkWhy.getInstance().getConfig();
        String  apiKey    = cfg.getString ("api_rest.key",         "");
        boolean useHttps  = HttpHost.isHttps();

        if ((apiKey == null || apiKey.isEmpty()) && !cfg.getBoolean("api_rest.allow_no_key", false)) {
            log.severe("[ApiServer] api_rest.key est vide : le serveur API n'est PAS démarré (il serait ouvert à tout le monde).");
            log.severe("[ApiServer] Définis une clef dans api_rest.key, ou mets api_rest.allow_no_key: true pour l'autoriser sans clef.");
            return false;
        }
        if (apiKey == null || apiKey.isEmpty()) {
            log.warning("[ApiServer] Aucune clef API définie (allow_no_key) : n'importe qui pouvant joindre le port peut envoyer des requêtes.");
        }

        if (!HttpHost.acquire("api")) return false;
        HttpHost.setRoot(exchange -> handleRequest(exchange, apiKey, useHttps));
        started = true;
        log.info("[ApiServer] API démarrée.");
        return true;
    }

    public void stop() {
        if (!started) return;
        started = false;
        HttpHost.setRoot(null);
        HttpHost.release("api");
        log.info("[ApiServer] API arrêtée.");
    }


    // ─────────────────────────────────────────────────────────────────────────
    // Traitement d'une requête entrante
    // ─────────────────────────────────────────────────────────────────────────

    private void handleRequest(HttpExchange exchange, String apiKey, boolean expectedHttps) {
        try {
            // ── 1. Vérifier la clef API (si définie) avant de lire le moindre octet du corps ──
            if (apiKey != null && !apiKey.isEmpty()) {
                String headerKey = exchange.getRequestHeaders().getFirst("X-Api-Key");
                if (headerKey == null || !java.security.MessageDigest.isEqual(
                        apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        headerKey.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                    // Clef absente ou incorrecte → silence total
                    exchange.close();
                    return;
                }
            }

            // ── 2. Lire le body (taille limitée) ──
            String declaredLength = exchange.getRequestHeaders().getFirst("Content-Length");
            boolean tooLarge = false;
            try {
                tooLarge = declaredLength != null && Long.parseLong(declaredLength.trim()) > MAX_BODY_BYTES;
            } catch (NumberFormatException ignored) {
                // en-tête invalide : la lecture bornée ci-dessous tranche
            }
            byte[] bytes = null;
            if (!tooLarge) {
                try (InputStream is = exchange.getRequestBody()) {
                    bytes = is.readNBytes(MAX_BODY_BYTES + 1);
                } catch (Exception e) {
                    // Corps illisible → on ne fait rien, pas de réponse
                    exchange.close();
                    return;
                }
                tooLarge = bytes.length > MAX_BODY_BYTES;
            }
            if (tooLarge) {
                log.warning("[ApiServer] Requête refusée : corps supérieur à " + MAX_BODY_BYTES + " octets ("
                        + exchange.getRemoteAddress() + ").");
                exchange.sendResponseHeaders(413, -1);
                exchange.close();
                return;
            }
            String body = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

            // ── 3. Construire l'objet API_request ──
            String method = exchange.getRequestMethod();
            String path   = exchange.getRequestURI().getPath();
            String query  = exchange.getRequestURI().getQuery(); // peut être null

            API_request req = new API_request(exchange, method, path, query, body);

            // ── 4. Passer sur le thread principal Bukkit et fire l'event ──
            Bukkit.getScheduler().runTask(SkWhy.getInstance(), () -> {
                try {
                    API_request_event event = new API_request_event(req);
                    Bukkit.getPluginManager().callEvent(event);
                } catch (Throwable t) {
                    log.log(java.util.logging.Level.WARNING, "[ApiServer] Erreur dans le traitement de la requête "
                            + req + " : " + t, t);
                } finally {
                    // Si personne n'a répondu via le type API_request, on ferme proprement
                    req.closeIfNotReplied();
                }
            });

        } catch (Throwable e) {
            log.warning("[ApiServer] Requête abandonnée suite à une erreur : " + e);
            exchange.close();
        }
    }

}
