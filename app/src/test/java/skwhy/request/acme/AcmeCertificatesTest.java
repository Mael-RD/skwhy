package skwhy.request.acme;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AcmeCertificatesTest {

    private static AcmeCertificates.Settings settings(String yaml) throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(yaml);
        return AcmeCertificates.Settings.from(cfg);
    }

    @Test
    void zoneAndChallengeNameAreDerivedFromTheDomain() throws Exception {
        var s = settings("api_rest:\n  acme:\n    enabled: true\n    domain: api.proxti.fr\n");
        assertEquals("proxti.fr", s.ovhZone());
        assertEquals("_acme-challenge.api", s.challengeSubDomain());

        var apex = settings("api_rest:\n  acme:\n    domain: proxti.fr\n");
        assertEquals("_acme-challenge", apex.challengeSubDomain());

        var deep = settings("api_rest:\n  acme:\n    domain: a.b.proxti.fr\n    ovh:\n      zone: proxti.fr\n");
        assertEquals("_acme-challenge.a.b", deep.challengeSubDomain());
    }

    @Test
    void incompleteSettingsAreReported() throws Exception {
        assertNotNull(settings("api_rest:\n  acme:\n    enabled: true\n").problem());
        var ok = settings("api_rest:\n  acme:\n    domain: api.proxti.fr\n    email: a@b.fr\n    ovh:\n      application_key: k\n"
                + "      application_secret: s\n      consumer_key: c\n");
        assertNull(ok.problem());
        assertTrue(ok.staging(), "staging par defaut");
        assertEquals(30, ok.renewBeforeDays());
    }

    @Test
    void ovhSignatureIsSha1OfTheCanonicalString() throws Exception {
        String sig = OvhDns.signature("secret", "consumer", "POST", "https://eu.api.ovh.com/1.0/x", "{}", 1700000000L);
        assertTrue(sig.startsWith("$1$"));
        assertEquals(3 + 40, sig.length());
        var md = java.security.MessageDigest.getInstance("SHA-1")
                .digest("secret+consumer+POST+https://eu.api.ovh.com/1.0/x+{}+1700000000".getBytes());
        assertEquals("$1$" + java.util.HexFormat.of().formatHex(md), sig);
    }

    @Test
    void ovhEndpoints() {
        assertEquals("https://eu.api.ovh.com/1.0", OvhDns.baseUrl("ovh-eu"));
        assertEquals("https://eu.api.ovh.com/1.0", OvhDns.baseUrl(""));
        assertEquals("https://ca.api.ovh.com/1.0", OvhDns.baseUrl("ovh-ca"));
        assertEquals("https://x.test/1.0", OvhDns.baseUrl("https://x.test/1.0/"));
    }

    @Test
    void missingOrGarbageCertificateNeedsIssuing(@TempDir File dir) throws Exception {
        File cert = new File(dir, "c.crt");
        File key = new File(dir, "k.key");
        assertTrue(AcmeCertificates.needsIssuing(cert, key, "api.proxti.fr", 30, Instant.now()));
        Files.writeString(cert.toPath(), "pas un certificat");
        Files.writeString(key.toPath(), "x");
        assertTrue(AcmeCertificates.needsIssuing(cert, key, "api.proxti.fr", 30, Instant.now()));
    }
}
