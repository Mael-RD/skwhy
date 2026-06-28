package skwhy.request;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import skwhy.SkWhy;
import skwhy.modules.API.events.API_request_event;
import skwhy.modules.API.types.API_request;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class APIserver {

    private HttpServer server; // HttpServer ou HttpsServer, même interface
    private final Logger log = SkWhy.getInstance().getLogger();

    public boolean start() {
        FileConfiguration cfg = SkWhy.getInstance().getConfig();
        int     port      = cfg.getInt    ("api_rest.port",        443);
        String  apiKey    = cfg.getString ("api_rest.key",         "");
        boolean useHttps  = cfg.getBoolean("api_rest.use_https",   true);
        String  certPath  = cfg.getString ("api_rest.certificat",  "certificat.cert");
        String  keyPath   = cfg.getString ("api_rest.private_key", "key.key");

        try {
            InetSocketAddress addr = new InetSocketAddress(port);

            if (useHttps) {
                // ── HTTPS ──
                File dataFolder = SkWhy.getInstance().getDataFolder();
                File certFile   = new File(dataFolder, certPath);
                File keyFile    = new File(dataFolder, keyPath);

                if (!certFile.exists() || !keyFile.exists()) {
                    log.severe("[ApiServer] Certificat ou clef privée introuvable. Serveur HTTPS non démarré.");
                    log.severe("[ApiServer] Attendu : " + certFile.getAbsolutePath());
                    log.severe("[ApiServer] Attendu : " + keyFile.getAbsolutePath());
                    return false;
                }

                SSLContext sslContext = buildSSLContext(certFile, keyFile);
                if (sslContext == null) return false;

                HttpsServer httpsServer = HttpsServer.create(addr, 0);
                httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                    @Override
                    public void configure(HttpsParameters params) {
                        SSLContext ctx    = getSSLContext();
                        SSLParameters sslParams = ctx.getDefaultSSLParameters();
                        params.setSSLParameters(sslParams);
                    }
                });
                server = httpsServer;
                log.info("[ApiServer] Mode HTTPS activé sur le port " + port);

            } else {
                // ── HTTP ──
                server = HttpServer.create(addr, 0);
                log.info("[ApiServer] Mode HTTP activé sur le port " + port);
            }

            // Handler principal — toutes les routes passent ici
            server.createContext("/", exchange -> handleRequest(exchange, apiKey, useHttps));
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            log.info("[ApiServer] Serveur démarré sur le port " + port + ".");
            return true;

        } catch (Exception e) {
            log.severe("[ApiServer] Impossible de démarrer le serveur : " + e.getMessage());
            return false;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            log.info("[ApiServer] Serveur arrêté.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Traitement d'une requête entrante
    // ─────────────────────────────────────────────────────────────────────────

    private void handleRequest(HttpExchange exchange, String apiKey, boolean expectedHttps) {
        try {
            // ── 1. Vérifier que le protocole correspond à la config ──
            // HttpsServer ne reçoit que du HTTPS, HttpServer que du HTTP.
            // Si on reçoit une requête ici c'est que le protocole est correct.
            // (Un client HTTP qui frappe un port HTTPS obtient une erreur SSL avant d'arriver ici.)

            // ── 2. Lire le body ──
            String body;
            try (InputStream is = exchange.getRequestBody()) {
                body = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                // Corps illisible → on ne fait rien, pas de réponse
                exchange.close();
                return;
            }

            // ── 3. Vérifier la clef API (si définie) ──
            if (apiKey != null && !apiKey.isEmpty()) {
                String headerKey = exchange.getRequestHeaders().getFirst("X-Api-Key");
                if (!apiKey.equals(headerKey)) {
                    // Clef absente ou incorrecte → silence total
                    exchange.close();
                    return;
                }
            }

            // ── 4. Construire l'objet API_request ──
            String method = exchange.getRequestMethod();
            String path   = exchange.getRequestURI().getPath();
            String query  = exchange.getRequestURI().getQuery(); // peut être null

            API_request req = new API_request(exchange, method, path, query, body);

            // ── 5. Passer sur le thread principal Bukkit et fire l'event ──
            Bukkit.getScheduler().runTask(SkWhy.getInstance(), () -> {
                API_request_event event = new API_request_event(req);
                Bukkit.getPluginManager().callEvent(event);
                // Si personne n'a répondu via le type API_request, on ferme proprement
                req.closeIfNotReplied();
            });

        } catch (Exception e) {
            // Toute erreur inattendue → on ferme silencieusement
            exchange.close();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Construction du SSLContext depuis cert + clef PEM
    // ─────────────────────────────────────────────────────────────────────────

    private SSLContext buildSSLContext(File certFile, File keyFile) {
        try {
            // Bukkit / Paper embarque BouncyCastle; sinon il faudra un KeyStore PKCS12
            // On utilise ici un KeyStore PKCS12 généré depuis les fichiers PEM via openssl :
            //   openssl pkcs12 -export -in certificat.cert -inkey key.key -out keystore.p12 -passout pass:changeit
            // Si tu préfères rester en .cert/.key bruts, remplace par une lib comme Bouncy Castle.

            // Pour simplifier, on essaie de charger un keystore PKCS12 dont le nom
            // est dérivé du cert (sans extension → .p12)
            String ksName = certFile.getName().replaceAll("\\.[^.]+$", "") + ".p12";
            File   ksFile = new File(certFile.getParentFile(), ksName);

            if (!ksFile.exists()) {
                log.severe("[ApiServer] KeyStore PKCS12 introuvable : " + ksFile.getAbsolutePath());
                log.severe("[ApiServer] Générez-le avec : openssl pkcs12 -export -in "
                        + certFile.getName() + " -inkey " + keyFile.getName()
                        + " -out " + ksName + " -passout pass:changeit");
                return null;
            }

            char[] password = "changeit".toCharArray();
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream ksStream = Files.newInputStream(ksFile.toPath())) {
                ks.load(ksStream, password);
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, password);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, null);
            return ctx;

        } catch (Exception e) {
            log.severe("[ApiServer] Erreur SSL : " + e.getMessage());
            return null;
        }
    }
}