package skwhy.apiclient;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Le client contre un vrai serveur HTTP local (aucune dépendance à Internet). */
class ApiClientTest {

    private static HttpServer server;
    private static ApiClient client;
    private static String base;
    private static final ApiClientSettings SETTINGS = ApiClientSettings.defaults();

    @BeforeAll
    static void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/echo", ApiClientTest::echo);
        server.createContext("/status/404", ex -> reply(ex, 404, "nope"));
        server.createContext("/status/500", ex -> reply(ex, 500, "boom"));
        server.createContext("/redirect", ex -> {
            ex.getResponseHeaders().set("Location", "/echo?from=redirect");
            ex.sendResponseHeaders(Integer.parseInt(ex.getRequestURI().getQuery() == null ? "302" : ex.getRequestURI().getQuery()), -1);
            ex.close();
        });
        server.createContext("/to-other-host", ex -> {
            ex.getResponseHeaders().set("Location", "http://127.0.0.2:" + server.getAddress().getPort() + "/echo");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.createContext("/loop", ex -> {
            ex.getResponseHeaders().set("Location", "/loop");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.createContext("/slow", ex -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
                // ignoré
            }
            reply(ex, 200, "late");
        });
        server.createContext("/big", ex -> reply(ex, 200, "x".repeat(5000)));
        server.createContext("/latin1", ex -> {
            byte[] bytes = "café".getBytes(StandardCharsets.ISO_8859_1);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=ISO-8859-1");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.createContext("/head", ex -> {
            ex.getResponseHeaders().set("X-Test", "yes");
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new ApiClient(4);
    }

    @AfterAll
    static void stop() {
        server.stop(0);
        client.shutdown();
    }

    /** Renvoie « méthode|chemin?requête|Content-Type|Authorization|X-Key|corps ». */
    private static void echo(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String text = ex.getRequestMethod() + "|" + ex.getRequestURI().getRawPath()
            + (ex.getRequestURI().getRawQuery() == null ? "" : "?" + ex.getRequestURI().getRawQuery())
            + "|" + ex.getRequestHeaders().getFirst("Content-Type")
            + "|" + ex.getRequestHeaders().getFirst("Authorization")
            + "|" + ex.getRequestHeaders().getFirst("X-Key")
            + "|" + body;
        ex.getResponseHeaders().add("X-Multi", "a");
        ex.getResponseHeaders().add("X-Multi", "b");
        reply(ex, 200, text);
    }

    private static void reply(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private static ApiResponse run(ApiCall call) {
        return client.execute(call, SETTINGS, call.timeoutMillis() != null ? call.timeoutMillis() : 5000);
    }

    @Test
    void getWithQueryAndHeaders() {
        ApiCall call = new ApiCall(base + "/echo?a=1");
        call.query().put("q", "x y");
        call.headers().put("X-Key", "secret");
        ApiResponse r = run(call);
        assertNull(r.error());
        assertEquals(200, r.status());
        assertTrue(r.isSuccessful());
        assertEquals("GET|/echo?a=1&q=x%20y|null|null|secret|", r.body());
        assertEquals("a, b", r.header("x-multi"), "several values are joined and names are case insensitive");
        assertTrue(r.headerNames().stream().anyMatch(n -> n.equalsIgnoreCase("x-multi")));
        assertTrue(r.durationMillis() >= 0);
        assertEquals(base + "/echo?a=1&q=x%20y", r.url());
    }

    @Test
    void postJsonBodyWithBearer() {
        ApiCall call = new ApiCall(base + "/echo");
        call.method("POST");
        call.json("{\"name\":\"é\"}");
        call.bearer("tok");
        ApiResponse r = run(call);
        assertEquals("POST|/echo|application/json|Bearer tok|null|{\"name\":\"é\"}", r.body());
    }

    @Test
    void putPatchDeleteAndForm() {
        for (String m : List.of("PUT", "PATCH", "DELETE")) {
            ApiCall call = new ApiCall(base + "/echo");
            call.method(m);
            call.body("b");
            assertEquals(m + "|/echo|null|null|null|b", run(call).body(), m);
        }
        ApiCall form = new ApiCall(base + "/echo");
        form.method("POST");
        form.form().put("a", "1 2");
        form.form().put("b", "é");
        assertEquals("POST|/echo|application/x-www-form-urlencoded|null|null|a=1%202&b=%C3%A9", run(form).body());
    }

    @Test
    void httpErrorKeepsItsStatusAndBody() {
        ApiResponse notFound = run(new ApiCall(base + "/status/404"));
        assertEquals(404, notFound.status());
        assertNull(notFound.error(), "an HTTP error is not a network error");
        assertEquals("nope", notFound.body());
        assertFalse(notFound.isSuccessful());
        ApiResponse boom = run(new ApiCall(base + "/status/500"));
        assertEquals(500, boom.status());
        assertEquals("boom", boom.body());
    }

    @Test
    void redirectsAreFollowedOrNot() {
        ApiResponse followed = run(new ApiCall(base + "/redirect"));
        assertEquals(200, followed.status());
        assertEquals("GET|/echo?from=redirect|null|null|null|", followed.body());
        assertEquals(base + "/echo?from=redirect", followed.url());

        ApiCall stay = new ApiCall(base + "/redirect");
        stay.followRedirects(false);
        ApiResponse r = run(stay);
        assertEquals(302, r.status());
        assertEquals("/echo?from=redirect", r.header("Location"));
    }

    @Test
    void redirectChangesPostToGetOnlyWhenRequired() {
        ApiCall see = new ApiCall(base + "/redirect?303");
        see.method("POST");
        see.body("payload");
        see.json("{}");
        assertEquals("GET|/echo?from=redirect|null|null|null|", run(see).body(), "303 -> GET without body");

        ApiCall keep = new ApiCall(base + "/redirect?307");
        keep.method("POST");
        keep.body("payload");
        assertEquals("POST|/echo?from=redirect|null|null|null|payload", run(keep).body(), "307 keeps the method and the body");
    }

    @Test
    void authorizationIsDroppedWhenRedirectedToAnotherHost() {
        ApiCall call = new ApiCall(base + "/to-other-host");
        call.bearer("tok");
        assertEquals("GET|/echo|null|null|null|", run(call).body());
    }

    @Test
    void redirectLoopStopsAtTheLimit() {
        ApiResponse r = run(new ApiCall(base + "/loop"));
        assertEquals(302, r.status(), "after max_redirects the last redirect is returned as is");
    }

    @Test
    void timeoutGivesStatusZero() {
        ApiCall call = new ApiCall(base + "/slow");
        call.timeoutMillis(300L);
        long start = System.nanoTime();
        ApiResponse r = run(call);
        assertEquals(0, r.status());
        assertNotNull(r.error());
        assertTrue(r.error().startsWith("timeout"), r.error());
        assertFalse(r.isSuccessful());
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1800);
    }

    @Test
    void asyncTimeoutCompletesTheFutureEvenIfTheServerStalls() throws Exception {
        ApiCall call = new ApiCall(base + "/slow");
        call.timeoutMillis(300L);
        ApiResponse r = client.sendAsync(call, SETTINGS).get(5, TimeUnit.SECONDS);
        assertEquals(0, r.status());
        assertTrue(r.error().startsWith("timeout"), r.error());
    }

    @Test
    void asyncSendDeliversTheResponse() throws Exception {
        ApiResponse r = client.sendAsync(new ApiCall(base + "/echo"), SETTINGS).get(5, TimeUnit.SECONDS);
        assertEquals(200, r.status());
    }

    @Test
    void sendingDoesNotDependOnLaterChangesToTheCall() throws Exception {
        ApiCall call = new ApiCall(base + "/echo");
        var future = client.sendAsync(call, SETTINGS);
        call.url("http://127.0.0.1:1/");
        assertEquals(200, future.get(5, TimeUnit.SECONDS).status());
    }

    @Test
    void connectionRefusedGivesStatusZero() throws IOException {
        int freePort;
        try (ServerSocket s = new ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))) {
            freePort = s.getLocalPort();
        }
        ApiResponse r = run(new ApiCall("http://127.0.0.1:" + freePort + "/"));
        assertEquals(0, r.status());
        assertNotNull(r.error());
        assertFalse(r.isSuccessful());
    }

    @Test
    void tooLargeResponseIsCutAndFlagged() {
        ApiClientSettings small = new ApiClientSettings(10, 100, 2, 5, true, false, List.of(), List.of(), "t");
        ApiResponse r = client.execute(new ApiCall(base + "/big"), small, 5000);
        assertEquals(200, r.status());
        assertEquals(100, r.body().length());
        assertNotNull(r.error());
        assertTrue(r.error().contains("too large"), r.error());
    }

    @Test
    void responseCharsetIsHonored() {
        assertEquals("café", run(new ApiCall(base + "/latin1")).body());
        assertEquals(StandardCharsets.UTF_8, ApiClient.charsetOf(null));
        assertEquals(StandardCharsets.UTF_8, ApiClient.charsetOf("text/plain; charset=nope"));
        assertEquals(StandardCharsets.ISO_8859_1, ApiClient.charsetOf("text/html; Charset=\"ISO-8859-1\""));
    }

    @Test
    void headHasNoBodyButHasHeaders() {
        ApiCall call = new ApiCall(base + "/head");
        call.method("HEAD");
        ApiResponse r = run(call);
        assertEquals(200, r.status());
        assertEquals("", r.body());
        assertEquals("yes", r.header("X-Test"));
    }

    @Test
    void refusedByTheGuardNeverConnects() {
        ApiClientSettings strict = new ApiClientSettings(10, 1024, 2, 5, false, false, List.of(), List.of(), "t");
        ApiResponse r = client.execute(new ApiCall(base + "/echo"), strict, 5000);
        assertEquals(0, r.status());
        assertTrue(r.error().contains("private"), r.error());
    }

    @Test
    void trustAllCertificatesNeedsTheConfigSwitch() {
        ApiCall call = new ApiCall(base + "/echo");
        call.trustAllCertificates(true);
        ApiResponse r = run(call);
        assertEquals(0, r.status());
        assertTrue(r.error().contains("allow_insecure_certificates"), r.error());

        ApiClientSettings permissive = new ApiClientSettings(10, 1024 * 1024, 2, 5, true, true, List.of(), List.of(), "t");
        assertEquals(200, client.execute(call, permissive, 5000).status());
    }

    @Test
    void invalidRequestsBecomeFailures() {
        ApiResponse empty = run(new ApiCall(""));
        assertEquals(0, empty.status());
        assertTrue(empty.error().startsWith("invalid request"), empty.error());

        ApiCall restricted = new ApiCall(base + "/echo");
        restricted.headers().put("Host", "evil");
        ApiResponse r = run(restricted);
        assertEquals(0, r.status());
        assertTrue(r.error().startsWith("invalid request"), r.error());

        ApiResponse scheme = run(new ApiCall("file:///etc/passwd"));
        assertEquals(0, scheme.status());
    }

    @Test
    void userAgentIsSentByDefault() {
        server.createContext("/ua", ex -> reply(ex, 200, String.valueOf(ex.getRequestHeaders().getFirst("User-Agent"))));
        ApiClientSettings s = new ApiClientSettings(10, 1024, 2, 5, true, false, List.of(), List.of(), "SkWhy/9.9");
        assertEquals("SkWhy/9.9", client.execute(new ApiCall(base + "/ua"), s, 5000).body());
        ApiCall custom = new ApiCall(base + "/ua");
        custom.headers().put("User-Agent", "mine");
        assertEquals("mine", client.execute(custom, s, 5000).body());
    }
}
