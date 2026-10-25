package skwhy.request;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import skwhy.SkWhy;
import skwhy.modules.ApiElements.events.API_request_event;
import skwhy.modules.ApiElements.types.API_request;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.util.logging.Logger;

import java.security.cert.X509Certificate;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.io.FileReader;
import java.io.Reader;

public class APIserver {

    /** Taille maximale acceptée pour le corps d'une requête. */
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final int MAX_THREADS = 8;
    private static final int MAX_QUEUED_REQUESTS = 128;

    private HttpServer server; // HttpServer ou HttpsServer, même interface
    private java.util.concurrent.ExecutorService executor;
    private final Logger log = SkWhy.getInstance().getLogger();

    public boolean start() {
        FileConfiguration cfg = SkWhy.getInstance().getConfig();
        int     port      = cfg.getInt    ("api_rest.port",        443);
        String  apiKey    = cfg.getString ("api_rest.key",         "");
        boolean useHttps  = cfg.getBoolean("api_rest.use_https",   true);
        String  certPath  = cfg.getString ("api_rest.certificate",  "certificate.cert");
        String  keyPath   = cfg.getString ("api_rest.private_key", "private.key");

        if ((apiKey == null || apiKey.isEmpty()) && !cfg.getBoolean("api_rest.allow_no_key", false)) {
            log.severe("[ApiServer] api_rest.key est vide : le serveur API n'est PAS démarré (il serait ouvert à tout le monde).");
            log.severe("[ApiServer] Définis une clef dans api_rest.key, ou mets api_rest.allow_no_key: true pour l'autoriser sans clef.");
            return false;
        }
        if (apiKey == null || apiKey.isEmpty()) {
            log.warning("[ApiServer] Aucune clef API définie (allow_no_key) : n'importe qui pouvant joindre le port peut envoyer des requêtes.");
        }

        // Délais de lecture/écriture du serveur JDK (secondes) ; ne remplace pas une valeur déjà définie par l'administrateur.
        if (System.getProperty("sun.net.httpserver.maxReqTime") == null) System.setProperty("sun.net.httpserver.maxReqTime", "30");
        if (System.getProperty("sun.net.httpserver.maxRspTime") == null) System.setProperty("sun.net.httpserver.maxRspTime", "30");

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
            java.util.concurrent.atomic.AtomicInteger threadNumber = new java.util.concurrent.atomic.AtomicInteger();
            executor = new java.util.concurrent.ThreadPoolExecutor(
                    2, MAX_THREADS, 30L, java.util.concurrent.TimeUnit.SECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(MAX_QUEUED_REQUESTS),
                    r -> {
                        Thread t = new Thread(r, "SkWhy-Api-" + threadNumber.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    },
                    new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
            server.setExecutor(executor);
            server.start();

            log.info("[ApiServer] Serveur démarré sur le port " + port + ".");
            return true;

        } catch (Exception e) {
            log.severe("[ApiServer] Impossible de démarrer le serveur : " + e);
            return false;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            log.info("[ApiServer] Serveur arrêté.");
        }
        if (executor != null) executor.shutdownNow();
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

    // ─────────────────────────────────────────────────────────────────────────
    // Construction du SSLContext depuis cert + clef PEM
    // ─────────────────────────────────────────────────────────────────────────

    private SSLContext buildSSLContext(File certFile, File keyFile) {
        try {
            // ── 1. Lire le certificat PEM ──
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert;
            try (InputStream certStream = Files.newInputStream(certFile.toPath())) {
                cert = (X509Certificate) cf.generateCertificate(certStream);
            }

            // ── 2. Lire la clef privée PKCS#1 (BEGIN RSA PRIVATE KEY) via BouncyCastle ──
            PrivateKey privateKey;
            try (Reader reader = new FileReader(keyFile)) {
                PEMParser pemParser = new PEMParser(reader);
                Object obj = pemParser.readObject();
                pemParser.close();

                JcaPEMKeyConverter converter = new JcaPEMKeyConverter()
                        .setProvider(new BouncyCastleProvider());

                if (obj instanceof PEMKeyPair) {
                    privateKey = converter.getKeyPair((PEMKeyPair) obj).getPrivate();
                } else if (obj instanceof PrivateKeyInfo) {
                    privateKey = converter.getPrivateKey((PrivateKeyInfo) obj);
                } else {
                    log.severe("[ApiServer] Format de clef non reconnu : " + obj.getClass().getName());
                    return null;
                }
            }

            // ── 3. Construire un KeyStore en mémoire ──
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            ks.setKeyEntry("key", privateKey, new char[0], new java.security.cert.Certificate[]{cert});

            // ── 4. Initialiser le SSLContext ──
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, new char[0]);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, null);
            return ctx;

        } catch (Exception e) {
            log.severe("[ApiServer] Erreur SSL : " + e.getMessage());
            return null;
        }
    }
}