package skwhy.apiclient;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ApiClientSettingsTest {

    private static ApiClientSettings load(String yaml) throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(yaml);
        return ApiClientSettings.fromConfig(cfg, "9.9.9");
    }

    @Test
    void missingSectionGivesDefaults() throws Exception {
        ApiClientSettings s = load("other: 1\n");
        assertEquals(10, s.defaultTimeoutSeconds());
        assertEquals(10L * 1024 * 1024, s.maxResponseBytes());
        assertEquals(8, s.maxConcurrent());
        assertEquals(5, s.maxRedirects());
        assertTrue(s.allowPrivateAddresses());
        assertFalse(s.allowInsecureCertificates());
        assertTrue(s.allowedHosts().isEmpty());
        assertEquals("SkWhy/9.9.9", s.userAgent());
    }

    @Test
    void valuesAreReadAndNormalized() throws Exception {
        ApiClientSettings s = load("""
            api_client:
              default_timeout_seconds: 3
              max_response_megabytes: 0.5
              max_concurrent: 2
              max_redirects: 0
              allow_private_addresses: false
              allow_insecure_certificates: true
              allowed_hosts: ["API.Example.com", " ", "b.org"]
              blocked_hosts: ["Evil.net"]
            """);
        assertEquals(3, s.defaultTimeoutSeconds());
        assertEquals(524288L, s.maxResponseBytes());
        assertEquals(2, s.maxConcurrent());
        assertEquals(0, s.maxRedirects());
        assertFalse(s.allowPrivateAddresses());
        assertTrue(s.allowInsecureCertificates());
        assertEquals(List.of("api.example.com", "b.org"), s.allowedHosts());
        assertEquals(List.of("evil.net"), s.blockedHosts());
    }

    @Test
    void nonsenseValuesAreClamped() throws Exception {
        ApiClientSettings s = load("api_client:\n  default_timeout_seconds: -4\n  max_concurrent: 0\n  max_redirects: -1\n  max_response_megabytes: 0\n");
        assertEquals(1, s.defaultTimeoutSeconds());
        assertEquals(1, s.maxConcurrent());
        assertEquals(0, s.maxRedirects());
        assertEquals(1024L, s.maxResponseBytes());
    }
}
