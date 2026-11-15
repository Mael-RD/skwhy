package skwhy.apiclient;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

class HostGuardTest {

    private static ApiClientSettings settings(boolean allowPrivate, List<String> allowed, List<String> blocked) {
        return new ApiClientSettings(10, 1024, 2, 5, allowPrivate, false, allowed, blocked, "t");
    }

    @Test
    void onlyHttpAndHttpsAreAllowed() {
        ApiClientSettings s = ApiClientSettings.defaults();
        assertNull(HostGuard.check(URI.create("http://example.com/a"), s));
        assertNull(HostGuard.check(URI.create("HTTPS://example.com/a"), s));
        assertNotNull(HostGuard.check(URI.create("file:///etc/passwd"), s));
        assertNotNull(HostGuard.check(URI.create("ftp://example.com/"), s));
        assertNotNull(HostGuard.check(URI.create("/relative"), s));
        assertNotNull(HostGuard.check(URI.create("http:///nohost"), s));
    }

    @Test
    void privateAddressesArePermittedByDefaultAndRefusedOnRequest() {
        assertNull(HostGuard.check(URI.create("http://127.0.0.1:8080/"), ApiClientSettings.defaults()));
        ApiClientSettings strict = settings(false, List.of(), List.of());
        for (String url : new String[]{ "http://127.0.0.1/", "http://localhost/", "http://10.1.2.3/", "http://192.168.0.5/", "http://172.20.0.1/",
                "http://169.254.169.254/latest/meta-data", "http://0.0.0.0/", "http://[::1]/", "http://[fd00::1]/", "http://[fe80::1]/" }) {
            String reason = HostGuard.check(URI.create(url), strict);
            assertNotNull(reason, url);
            assertTrue(reason.contains("private"), url + " -> " + reason);
        }
        assertNull(HostGuard.check(URI.create("http://8.8.8.8/"), strict));
        assertNull(HostGuard.check(URI.create("http://[2001:4860:4860::8888]/"), strict));
    }

    @Test
    void allowedAndBlockedHostsMatchSubdomains() {
        ApiClientSettings allowOnly = settings(true, List.of("api.example.com"), List.of());
        assertNull(HostGuard.check(URI.create("https://api.example.com/x"), allowOnly));
        assertNull(HostGuard.check(URI.create("https://v2.api.example.com/x"), allowOnly));
        assertNotNull(HostGuard.check(URI.create("https://example.com/x"), allowOnly));
        assertNotNull(HostGuard.check(URI.create("https://evilapi.example.com.attacker.net/x"), allowOnly));
        assertNotNull(HostGuard.check(URI.create("https://notapi.example.com.fr/x"), allowOnly));

        ApiClientSettings blocking = settings(true, List.of(), List.of("evil.net"));
        assertNotNull(HostGuard.check(URI.create("https://evil.net/"), blocking));
        assertNotNull(HostGuard.check(URI.create("https://sub.EVIL.net/"), blocking));
        assertNull(HostGuard.check(URI.create("https://notevil.net/"), blocking));
    }

    @Test
    void privateRangesAreDetected() throws Exception {
        assertTrue(HostGuard.isPrivate(InetAddress.getByName("172.31.255.255")));
        assertFalse(HostGuard.isPrivate(InetAddress.getByName("172.32.0.1")));
        assertTrue(HostGuard.isPrivate(InetAddress.getByName("fc00::1")));
        assertFalse(HostGuard.isPrivate(InetAddress.getByName("2606:4700::1111")));
    }
}
