package skwhy.apiclient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * Client HTTP/HTTPS des requêtes sortantes : tout s'exécute hors du thread principal, jamais plus de
 * {@code max_concurrent} requêtes à la fois. Les redirections sont suivies à la main pour que
 * chaque adresse passe par {@link HostGuard}. Une requête ne lève jamais : un échec devient une
 * {@link ApiResponse} de statut 0 avec son motif.
 */
public final class ApiClient {

    private static final Pattern CHARSET = Pattern.compile("charset=\"?([A-Za-z0-9_.:-]+)\"?", Pattern.CASE_INSENSITIVE);

    private final ExecutorService executor;
    private final ScheduledExecutorService watchdog;
    private volatile HttpClient secure;
    private volatile HttpClient insecure;

    public ApiClient(int threads) {
        this.executor = Executors.newFixedThreadPool(Math.max(1, threads), daemon("skwhy-api-client"));
        this.watchdog = Executors.newSingleThreadScheduledExecutor(daemon("skwhy-api-timeout"));
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return runnable -> {
            Thread t = new Thread(runnable, name);
            t.setDaemon(true);
            return t;
        };
    }

    /** Envoie la requête (copiée) ; le futur se termine toujours, sur un thread de l'exécuteur ou du chien de garde. */
    public CompletableFuture<ApiResponse> sendAsync(ApiCall call, ApiClientSettings settings) {
        ApiCall c = call.copy();
        long timeout = c.timeoutMillis() != null ? c.timeoutMillis() : settings.defaultTimeoutSeconds() * 1000L;
        CompletableFuture<ApiResponse> future = new CompletableFuture<>();
        Abort abort = new Abort();
        long start = System.nanoTime();
        try {
            watchdog.schedule(() -> {
                if (future.complete(ApiResponse.failure("timeout after " + timeout + " ms", elapsed(start), c.url()))) abort.run();
            }, timeout, TimeUnit.MILLISECONDS);
            executor.execute(() -> {
                ApiResponse response;
                try {
                    response = execute(c, settings, timeout, abort, start);
                } catch (Throwable t) {
                    response = ApiResponse.failure("internal error: " + t, elapsed(start), c.url());
                }
                future.complete(response);
            });
        } catch (RuntimeException e) { // exécuteur arrêté
            future.complete(ApiResponse.failure("the api client is stopped", 0, c.url()));
        }
        return future;
    }

    /** Exécution bloquante (thread d'exécuteur ou tests). */
    public ApiResponse execute(ApiCall c, ApiClientSettings s, long timeoutMillis) {
        return execute(c, s, timeoutMillis, new Abort(), System.nanoTime());
    }

    private ApiResponse execute(ApiCall c, ApiClientSettings s, long timeout, Abort abort, long start) {
        String shownUrl = c.url();
        try {
            URI uri = c.buildUri();
            String method = c.method();
            String body = c.effectiveBody();
            Map<String, String> headers = c.effectiveHeaders();
            if (c.trustAllCertificates() && !s.allowInsecureCertificates()) {
                return ApiResponse.failure("'trust all certificates' is disabled (api_client.allow_insecure_certificates is false)", elapsed(start), shownUrl);
            }
            HttpClient client = c.trustAllCertificates() ? insecureClient() : secureClient();
            for (int hop = 0; ; hop++) {
                shownUrl = uri.toString();
                String denied = HostGuard.check(uri, s);
                if (denied != null) return ApiResponse.failure(denied, elapsed(start), shownUrl);

                HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(timeout));
                for (Map.Entry<String, String> h : headers.entrySet()) b.header(h.getKey(), h.getValue());
                if (!headers.containsKey("User-Agent")) b.header("User-Agent", s.userAgent());
                b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

                HttpResponse<InputStream> r = client.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
                int code = r.statusCode();
                String location = r.headers().firstValue("location").orElse(null);
                if (c.followRedirects() && isRedirect(code) && location != null && hop < s.maxRedirects()) {
                    closeQuietly(r.body());
                    URI next = uri.resolve(location);
                    if (!sameHost(uri, next)) headers.remove("Authorization");
                    if (code == 303 || ((code == 301 || code == 302) && !method.equals("GET") && !method.equals("HEAD"))) {
                        method = "GET";
                        body = null;
                        headers.remove("Content-Type");
                    }
                    uri = next;
                    continue;
                }
                return read(r, s, abort, start, uri.toString(), method.equals("HEAD"));
            }
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure("invalid request: " + e.getMessage(), elapsed(start), shownUrl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ApiResponse.failure("interrupted", elapsed(start), shownUrl);
        } catch (HttpTimeoutException e) {
            return ApiResponse.failure("timeout after " + timeout + " ms", elapsed(start), shownUrl);
        } catch (ConnectException e) {
            return ApiResponse.failure("connection failed: " + describe(e), elapsed(start), shownUrl);
        } catch (SSLException e) {
            return ApiResponse.failure("ssl error: " + describe(e), elapsed(start), shownUrl);
        } catch (IOException e) {
            return ApiResponse.failure(abort.aborted ? "timeout after " + timeout + " ms" : "network error: " + describe(e), elapsed(start), shownUrl);
        }
    }

    private ApiResponse read(HttpResponse<InputStream> r, ApiClientSettings s, Abort abort, long start, String url, boolean head) throws IOException {
        Map<String, List<String>> headers = new HashMap<>(r.headers().map());
        if (head) {
            closeQuietly(r.body());
            return new ApiResponse(r.statusCode(), "", headers, elapsed(start), null, url);
        }
        String error = null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = r.body()) {
            abort.stream = in;
            byte[] buffer = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buffer)) >= 0) {
                total += n;
                if (total > s.maxResponseBytes()) {
                    int keep = (int) (n - (total - s.maxResponseBytes()));
                    out.write(buffer, 0, Math.max(0, keep));
                    error = "response too large (more than " + s.maxResponseBytes() + " bytes)";
                    break;
                }
                out.write(buffer, 0, n);
            }
        }
        String type = r.headers().firstValue("content-type").orElse("");
        return new ApiResponse(r.statusCode(), new String(out.toByteArray(), charsetOf(type)), headers, elapsed(start), error, url);
    }

    static Charset charsetOf(String contentType) {
        Matcher m = CHARSET.matcher(contentType == null ? "" : contentType);
        if (m.find()) {
            try {
                return Charset.forName(m.group(1));
            } catch (RuntimeException ignored) {
                // charset inconnu : UTF-8
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private static boolean sameHost(URI a, URI b) {
        return a.getHost() != null && b.getHost() != null && a.getHost().equalsIgnoreCase(b.getHost()) && a.getPort() == b.getPort()
            && String.valueOf(a.getScheme()).equalsIgnoreCase(String.valueOf(b.getScheme()));
    }

    private static String describe(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) m = e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage() : e.getClass().getSimpleName();
        return m;
    }

    private static long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // rien à faire
        }
    }

    private HttpClient secureClient() {
        HttpClient c = secure;
        if (c == null) {
            c = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(30)).build();
            secure = c;
        }
        return c;
    }

    private HttpClient insecureClient() {
        HttpClient c = insecure;
        if (c == null) {
            try {
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, new TrustManager[]{ new TrustAll() }, new SecureRandom());
                c = HttpClient.newBuilder().sslContext(ctx).followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(30)).build();
            } catch (java.security.GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
            insecure = c;
        }
        return c;
    }

    public void shutdown() {
        executor.shutdownNow();
        watchdog.shutdownNow();
    }

    /** Ferme le flux de la réponse en cours de lecture quand le délai est dépassé. */
    private static final class Abort implements Runnable {
        volatile boolean aborted;
        volatile InputStream stream;

        @Override
        public void run() {
            aborted = true;
            InputStream in = stream;
            if (in != null) closeQuietly(in);
        }
    }

    /** Accepte tout certificat (et tout nom d'hôte : l'extension SSLEngine/Socket remplace la vérification d'identité). */
    private static final class TrustAll extends X509ExtendedTrustManager {
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, java.net.Socket socket) { }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, java.net.Socket socket) { }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) { }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) { }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }
}
