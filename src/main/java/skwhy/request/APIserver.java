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
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.util.concurrent.Executors;
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

    private HttpServer server; // HttpServer ou HttpsServer, même interface
    private final Logger log = SkWhy.getInstance().getLogger();

    public boolean start() {
        FileConfiguration cfg = SkWhy.getInstance().getConfig();
        int     port      = cfg.getInt    ("api_rest.port",        443);
        String  apiKey    = cfg.getString ("api_rest.key",         "");
        boolean useHttps  = cfg.getBoolean("api_rest.use_https",   true);
        String  certPath  = cfg.getString ("api_rest.certificate",  "certificate.cert");
        String  keyPath   = cfg.getString ("api_rest.private_key", "private.key");

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