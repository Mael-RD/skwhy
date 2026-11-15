package skwhy.request;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bukkit.configuration.file.FileConfiguration;

import skwhy.SkWhy;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import skwhy.request.acme.AcmeCertificates;

/**
 * Serveur HTTP(S) unique de SkWhy (port, HTTPS et certificat de {@code api_rest.*}), partagé entre l'API REST et l'éditeur
 * de quêtes. Chaque utilisateur l'{@linkplain #acquire acquiert} et le {@linkplain #release libère} : le serveur démarre
 * au premier et s'arrête au dernier, l'un ne dépend donc pas du démarrage de l'autre.
 *
 * <p>Tout chemin que personne ne prend en charge est fermé sans réponse (comme une mauvaise clef de l'API).
 */
public final class HttpHost {

    private static final int MAX_THREADS = 8;
    private static final int MAX_QUEUED_REQUESTS = 128;

    private static final Consumer<HttpExchange> SILENT = HttpExchange::close;

    private static HttpServer server; // HttpServer ou HttpsServer, même interface
    private static ExecutorService executor;
    private static final Set<String> users = new LinkedHashSet<>();
    private static volatile Consumer<HttpExchange> root = SILENT;

    private static AcmeCertificates acme;
    private static BukkitTask renewalTask;

    private HttpHost() {}

    /** Gestionnaire du certificat Let's Encrypt, ou {@code null} si {@code api_rest.acme.enabled} est faux ou la configuration incomplète. */
    private static AcmeCertificates newAcme(FileConfiguration cfg, File dataFolder, File certFile, File keyFile) {
        AcmeCertificates.Settings settings = AcmeCertificates.Settings.from(cfg);
        if (!settings.enabled()) return null;
        String problem = settings.problem();
        if (problem != null) {
            log().severe("[Http] Certificat automatique désactivé : " + problem + ".");
            return null;
        }
        return new AcmeCertificates(settings, dataFolder, certFile, keyFile, log());
    }

    /** Vérifie le certificat 2 min après le démarrage puis toutes les 12 h ; un certificat renouvelé est rechargé à chaud. */
    private static void scheduleRenewal(HttpsServer https, File certFile, File keyFile) {
        AcmeCertificates manager = acme;
        long period = 12L * 3600 * 20;
        renewalTask = Bukkit.getScheduler().runTaskTimerAsynchronously(SkWhy.getInstance(), () -> {
            try {
                if (!manager.needsIssuing()) return;
                log().info("[Http] Le certificat expire bientôt : renouvellement.");
                if (!manager.issue()) return;
                SSLContext ctx = buildSSLContext(certFile, keyFile);
                if (ctx == null) return;
                https.setHttpsConfigurator(httpsConfigurator(ctx));
                log().info("[Http] Nouveau certificat chargé.");
            } catch (Throwable t) {
                log().severe("[Http] Renouvellement du certificat : " + t);
            }
        }, 2400L, period);
    }

    private static HttpsConfigurator httpsConfigurator(SSLContext ctx) {
        return new HttpsConfigurator(ctx) {
            @Override
            public void configure(HttpsParameters params) {
                params.setSSLParameters(getSSLContext().getDefaultSSLParameters());
            }
        };
    }

    private static Logger log() {
        return SkWhy.getInstance().getLogger();
    }

    /** Démarre le serveur s'il ne tourne pas encore et y inscrit {@code user}. @return {@code false} si le serveur ne peut pas démarrer */
    public static synchronized boolean acquire(String user) {
        if (server == null && !startServer()) return false;
        users.add(user);
        return true;
    }

    /** Retire {@code user} ; le serveur s'arrête quand plus personne ne l'utilise. */
    public static synchronized void release(String user) {
        users.remove(user);
        if (!users.isEmpty() || server == null) return;
        if (renewalTask != null) renewalTask.cancel();
        renewalTask = null;
        acme = null;
        server.stop(0);
        server = null;
        if (executor != null) executor.shutdownNow();
        executor = null;
        root = SILENT;
        log().info("[Http] Serveur arrêté.");
    }

    /** Gestionnaire du chemin {@code /} (l'API REST) ; {@code null} le remet en « fermeture silencieuse ». */
    public static void setRoot(Consumer<HttpExchange> handler) {
        root = handler == null ? SILENT : handler;
    }

    /** Inscrit un préfixe de chemin (plus spécifique que {@code /}). Le serveur doit avoir été acquis. */
    public static synchronized HttpContext createContext(String path, HttpHandler handler) {
        if (server == null) throw new IllegalStateException("Serveur HTTP non démarré");
        return server.createContext(path, handler);
    }

    public static synchronized void removeContext(HttpContext context) {
        if (server != null && context != null) server.removeContext(context);
    }

    public static boolean isHttps() {
        return SkWhy.getInstance().getConfig().getBoolean("api_rest.use_https", true);
    }

    public static int port() {
        return SkWhy.getInstance().getConfig().getInt("api_rest.port", 443);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static boolean startServer() {
        FileConfiguration cfg = SkWhy.getInstance().getConfig();
        int port = port();
        boolean useHttps = isHttps();
        String certPath = cfg.getString("api_rest.certificate", "certificate.cert");
        String keyPath = cfg.getString("api_rest.private_key", "private.key");

        // Délais de lecture/écriture du serveur JDK (secondes) ; ne remplace pas une valeur déjà définie par l'administrateur.
        if (System.getProperty("sun.net.httpserver.maxReqTime") == null) System.setProperty("sun.net.httpserver.maxReqTime", "30");
        if (System.getProperty("sun.net.httpserver.maxRspTime") == null) System.setProperty("sun.net.httpserver.maxRspTime", "30");

        try {
            InetSocketAddress addr = new InetSocketAddress(port);
            HttpServer created;
            if (useHttps) {
                File dataFolder = SkWhy.getInstance().getDataFolder();
                File certFile = new File(dataFolder, certPath);
                File keyFile = new File(dataFolder, keyPath);
                acme = newAcme(cfg, dataFolder, certFile, keyFile);
                if (acme != null && acme.needsIssuing()) {
                    log().info("[Http] Certificat absent ou proche de l'expiration : demande à Let's Encrypt (peut durer une minute)...");
                    acme.issue();
                }
                if (!certFile.exists() || !keyFile.exists()) {
                    log().severe("[Http] Certificat ou clef privée introuvable. Serveur HTTPS non démarré.");
                    log().severe("[Http] Attendu : " + certFile.getAbsolutePath());
                    log().severe("[Http] Attendu : " + keyFile.getAbsolutePath());
                    return false;
                }
                SSLContext sslContext = buildSSLContext(certFile, keyFile);
                if (sslContext == null) return false;

                HttpsServer httpsServer = HttpsServer.create(addr, 0);
                httpsServer.setHttpsConfigurator(httpsConfigurator(sslContext));
                created = httpsServer;
                log().info("[Http] Mode HTTPS activé sur le port " + port);
            } else {
                created = HttpServer.create(addr, 0);
                log().info("[Http] Mode HTTP activé sur le port " + port);
            }

            created.createContext("/", exchange -> {
                try {
                    root.accept(exchange);
                } catch (Throwable t) {
                    exchange.close();
                }
            });
            AtomicInteger threadNumber = new AtomicInteger();
            executor = new ThreadPoolExecutor(
                    2, MAX_THREADS, 30L, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(MAX_QUEUED_REQUESTS),
                    r -> {
                        Thread t = new Thread(r, "SkWhy-Http-" + threadNumber.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    },
                    new ThreadPoolExecutor.CallerRunsPolicy());
            created.setExecutor(executor);
            created.start();
            server = created;
            if (acme != null && created instanceof HttpsServer https) scheduleRenewal(https, new File(SkWhy.getInstance().getDataFolder(), certPath),
                    new File(SkWhy.getInstance().getDataFolder(), keyPath));
            log().info("[Http] Serveur démarré sur le port " + port + ".");
            return true;
        } catch (Exception e) {
            log().severe("[Http] Impossible de démarrer le serveur : " + e);
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Construction du SSLContext depuis cert + clef PEM
    // ─────────────────────────────────────────────────────────────────────────

    private static SSLContext buildSSLContext(File certFile, File keyFile) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            // Toute la chaîne du fichier (certificat du serveur en premier, puis les intermédiaires)
            java.security.cert.Certificate[] chain;
            try (InputStream certStream = Files.newInputStream(certFile.toPath())) {
                chain = cf.generateCertificates(certStream).toArray(new java.security.cert.Certificate[0]);
            }
            if (chain.length == 0) {
                log().severe("[Http] Aucun certificat dans " + certFile.getName());
                return null;
            }

            PrivateKey privateKey;
            try (Reader reader = new FileReader(keyFile)) {
                PEMParser pemParser = new PEMParser(reader);
                Object obj = pemParser.readObject();
                pemParser.close();

                JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(new BouncyCastleProvider());
                if (obj instanceof PEMKeyPair) {
                    privateKey = converter.getKeyPair((PEMKeyPair) obj).getPrivate();
                } else if (obj instanceof PrivateKeyInfo) {
                    privateKey = converter.getPrivateKey((PrivateKeyInfo) obj);
                } else {
                    log().severe("[Http] Format de clef non reconnu : " + (obj == null ? "null" : obj.getClass().getName()));
                    return null;
                }
            }

            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            ks.setKeyEntry("key", privateKey, new char[0], chain);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, new char[0]);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, null);
            return ctx;
        } catch (Exception e) {
            log().severe("[Http] Erreur SSL : " + e.getMessage());
            return null;
        }
    }
}
