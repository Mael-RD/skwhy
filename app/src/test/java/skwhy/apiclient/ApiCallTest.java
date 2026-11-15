package skwhy.apiclient;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class ApiCallTest {

    @Test
    void methodIsNormalizedAndValidated() {
        ApiCall call = new ApiCall("http://x");
        assertEquals("GET", call.method());
        call.method(" post ");
        assertEquals("POST", call.method());
        for (String ok : new String[]{ "GET", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS" }) {
            call.method(ok);
            assertEquals(ok, call.method());
        }
        assertThrows(IllegalArgumentException.class, () -> call.method("TRACE"));
        assertThrows(IllegalArgumentException.class, () -> call.method(""));
        assertThrows(IllegalArgumentException.class, () -> call.method(null));
        assertEquals("OPTIONS", call.method(), "a refused method keeps the previous one");
    }

    @Test
    void queryParametersAreEncodedAndMergedWithTheUrlQuery() {
        ApiCall call = new ApiCall("https://api.example.com/search");
        call.query().put("q", "a b&c");
        call.query().put("é", "1=2");
        assertEquals("https://api.example.com/search?q=a%20b%26c&%C3%A9=1%3D2", call.buildUri().toString());

        ApiCall withQuery = new ApiCall("https://api.example.com/s?x=1");
        withQuery.query().put("page", "2");
        assertEquals("https://api.example.com/s?x=1&page=2", withQuery.buildUri().toString());

        ApiCall fragment = new ApiCall("https://api.example.com/s#top");
        fragment.query().put("a", "b");
        assertEquals("https://api.example.com/s?a=b#top", fragment.buildUri().toString());

        assertEquals("https://api.example.com/s", new ApiCall("https://api.example.com/s").buildUri().toString());
    }

    @Test
    void invalidUrlIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new ApiCall("").buildUri());
        assertThrows(IllegalArgumentException.class, () -> new ApiCall("http://exa mple.com").buildUri());
    }

    @Test
    void authenticationHeaders() {
        ApiCall bearer = new ApiCall("http://x");
        bearer.bearer("tok");
        assertEquals("Bearer tok", bearer.effectiveHeaders().get("authorization"));

        ApiCall basic = new ApiCall("http://x");
        basic.basic("user:pässword");
        String expected = "Basic " + Base64.getEncoder().encodeToString("user:pässword".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(expected, basic.effectiveHeaders().get("Authorization"));

        ApiCall explicit = new ApiCall("http://x");
        explicit.bearer("tok");
        explicit.headers().put("authorization", "Custom abc");
        assertEquals("Custom abc", explicit.effectiveHeaders().get("Authorization"), "an explicit header wins");

        bearer.bearer("");
        assertNull(bearer.bearer());
        assertFalse(bearer.effectiveHeaders().containsKey("Authorization"));
    }

    @Test
    void bodyJsonAndFormRules() {
        ApiCall form = new ApiCall("http://x");
        form.form().put("grant type", "a&b");
        form.form().put("n", "é");
        assertEquals("grant%20type=a%26b&n=%C3%A9", form.effectiveBody());
        assertEquals("application/x-www-form-urlencoded", form.effectiveHeaders().get("content-type"));

        form.json("{\"a\":1}");
        assertEquals("{\"a\":1}", form.effectiveBody(), "the body wins over the form");
        assertEquals("application/json", form.effectiveHeaders().get("Content-Type"));

        ApiCall custom = new ApiCall("http://x");
        custom.headers().put("Content-Type", "application/vnd.api+json");
        custom.json("{}");
        assertEquals("application/vnd.api+json", custom.effectiveHeaders().get("Content-Type"), "json() keeps an existing content type");

        assertNull(new ApiCall("http://x").effectiveBody());
    }

    @Test
    void headersAreCaseInsensitive() {
        ApiCall call = new ApiCall("http://x");
        call.headers().put("X-Api-Key", "1");
        call.headers().put("x-api-key", "2");
        assertEquals(1, call.headers().size());
        assertEquals("2", call.headers().get("X-API-KEY"));
    }

    @Test
    void copyIsIndependent() {
        ApiCall call = new ApiCall("http://x/");
        call.method("POST");
        call.headers().put("A", "1");
        call.query().put("q", "1");
        call.form().put("f", "1");
        call.body("b");
        call.timeoutMillis(1500L);
        call.followRedirects(false);
        call.trustAllCertificates(true);
        call.bearer("t");
        call.basic("u:p");

        ApiCall copy = call.copy();
        call.headers().put("B", "2");
        call.query().clear();
        call.url("http://other/");

        assertEquals("http://x/", copy.url());
        assertEquals("POST", copy.method());
        assertEquals(1, copy.headers().size());
        assertEquals("1", copy.query().get("q"));
        assertEquals("1", copy.form().get("f"));
        assertEquals("b", copy.body());
        assertEquals(1500L, copy.timeoutMillis());
        assertFalse(copy.followRedirects());
        assertTrue(copy.trustAllCertificates());
        assertEquals("t", copy.bearer());
        assertEquals("u:p", copy.basic());
    }

    @Test
    void timeoutBelowOneIsCleared() {
        ApiCall call = new ApiCall("http://x");
        call.timeoutMillis(0L);
        assertNull(call.timeoutMillis());
        call.timeoutMillis(-5L);
        assertNull(call.timeoutMillis());
        call.timeoutMillis(250L);
        assertEquals(250L, call.timeoutMillis());
        call.timeoutMillis(null);
        assertNull(call.timeoutMillis());
    }
}
