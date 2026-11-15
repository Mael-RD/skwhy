package skwhy.request.acme;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bukkit.configuration.file.FileConfiguration;
import org.shredzone.acme4j.Account;
import org.shredzone.acme4j.AccountBuilder;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Order;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.Status;
import org.shredzone.acme4j.challenge.Dns01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.CSRBuilder;
import org.shredzone.acme4j.util.KeyPairUtils;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

/**
 * Obtention et renouvellement du certificat du serveur HTTPS auprès de Let's Encrypt (ACME).
 * La preuve de possession du domaine est un TXT {@code _acme-challenge} posé par l'API OVH (DNS-01) : elle ne dépend d'aucun port.
 */
public final class AcmeCertificates {

    /** Réglages {@code api_rest.acme.*}. */
    public record Settings(boolean enabled, String domain, String email, int renewBeforeDays, boolean staging,
                           String ovhEndpoint, String ovhAppKey, String ovhAppSecret, String ovhConsumerKey, String ovhZone,
                           int dnsWaitSeconds) {

        public static Settings from(FileConfiguration cfg) {
            String p = "api_rest.acme.";
            String domain = cfg.getString(p + "domain", "").trim();
            String zone = cfg.getString(p + "ovh.zone", "").trim();
            return new Settings(
                    cfg.getBoolean(p + "enabled", false), domain, cfg.getString(p + "email", "").trim(),
                    Math.max(1, cfg.getInt(p + "renew_before_days", 30)), cfg.getBoolean(p + "staging", true),
                    cfg.getString(p + "ovh.endpoint", "ovh-eu"), cfg.getString(p + "ovh.application_key", "").trim(),
                    cfg.getString(p + "ovh.application_secret", "").trim(), cfg.getString(p + "ovh.consumer_key", "").trim(),
                    zone.isEmpty() ? zoneOf(domain) : zone, Math.max(0, cfg.getInt(p + "dns_wait_seconds", 45)));
        }

        /** Zone par défaut : les deux derniers libellés ({@code api.proxti.fr} -> {@code proxti.fr}). */
        static String zoneOf(String domain) {
            String[] parts = domain.split("\\.");
            return parts.length <= 2 ? domain : parts[parts.length - 2] + "." + parts[parts.length - 1];
        }

        /** Sous-domaine du défi dans la zone ({@code api.proxti.fr} -> {@code _acme-challenge.api}). */
        String challengeSubDomain() {
            String prefix = domain.equals(ovhZone) ? "" : domain.substring(0, domain.length() - ovhZone.length() - 1);
            return prefix.isEmpty() ? "_acme-challenge" : "_acme-challenge." + prefix;
        }

        /** Message décrivant ce qui manque, ou {@code null} si tout est renseigné. */
        public String problem() {
            if (domain.isEmpty()) return "api_rest.acme.domain est vide";
            if (!domain.endsWith(ovhZone)) return "le domaine " + domain + " n'est pas dans la zone " + ovhZone;
            if (email.isEmpty()) return "api_rest.acme.email est vide";
            if (ovhAppKey.isEmpty() || ovhAppSecret.isEmpty() || ovhConsumerKey.isEmpty()) return "clefs api_rest.acme.ovh.* incomplètes";
            return null;
        }
    }

    private final Settings s;
    private final File dataFolder;
    private final File certFile;
    private final File keyFile;
    private final Logger log;

    public AcmeCertificates(Settings settings, File dataFolder, File certFile, File keyFile, Logger log) {
        this.s = settings;
        this.dataFolder = dataFolder;
        this.certFile = certFile;
        this.keyFile = keyFile;
        this.log = log;
    }

    /** Vrai si le certificat manque, ne couvre pas le domaine, est expiré ou expire dans moins de {@code renewBeforeDays} jours. */
    public boolean needsIssuing() {
        return needsIssuing(certFile, keyFile, s.domain(), s.renewBeforeDays(), Instant.now());
    }

    static boolean needsIssuing(File certFile, File keyFile, String domain, int renewBeforeDays, Instant now) {
        if (!certFile.isFile() || !keyFile.isFile()) return true;
        try (var in = Files.newInputStream(certFile.toPath())) {
            Collection<? extends java.security.cert.Certificate> chain = CertificateFactory.getInstance("X.509").generateCertificates(in);
            if (chain.isEmpty()) return true;
            X509Certificate leaf = (X509Certificate) chain.iterator().next();
            if (!covers(leaf, domain)) return true;
            return leaf.getNotAfter().toInstant().isBefore(now.plus(Duration.ofDays(renewBeforeDays)));
        } catch (IOException | CertificateException | ClassCastException e) {
            return true;
        }
    }

    static boolean covers(X509Certificate cert, String domain) {
        try {
            var names = cert.getSubjectAlternativeNames();
            if (names == null) return false;
            for (List<?> n : names) {
                if (n.size() >= 2 && Integer.valueOf(2).equals(n.get(0)) && domain.equalsIgnoreCase(String.valueOf(n.get(1)))) return true;
            }
        } catch (CertificateException ignored) {
            // illisible : considéré comme ne couvrant pas le domaine
        }
        return false;
    }

    /**
     * Demande un nouveau certificat et écrit la chaîne complète dans le fichier du certificat et la clef dans celui de la clef.
     * Bloquant (jusqu'à plusieurs minutes) : ne pas appeler depuis le thread principal pendant la vie du serveur.
     *
     * @return {@code true} si les fichiers ont été remplacés
     */
    public synchronized boolean issue() {
        String problem = s.problem();
        if (problem != null) {
            log.severe("[Acme] Configuration incomplète : " + problem + ".");
            return false;
        }
        OvhDns dns = new OvhDns(s.ovhEndpoint(), s.ovhAppKey(), s.ovhAppSecret(), s.ovhConsumerKey(), s.ovhZone());
        int[] waits = {s.dnsWaitSeconds(), Math.max(s.dnsWaitSeconds(), 30) * 3};
        // acme4j trouve ses fournisseurs (Let's Encrypt...) par ServiceLoader avec le classloader du thread : celui du serveur ne voit pas le jar du plugin
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(AcmeCertificates.class.getClassLoader());
        // acme4j demande BouncyCastle par son nom ("BC") : il doit etre enregistre dans la JVM (ajoute a la fin, sans changer les fournisseurs existants)
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) Security.addProvider(new BouncyCastleProvider());
        try {
            for (int attempt = 0; attempt < waits.length; attempt++) {
                try {
                    order(dns, waits[attempt]);
                    return true;
                } catch (InterruptedException e) {
                    thread.interrupt();
                    return false;
                } catch (Exception e) {
                    log.warning("[Acme] Tentative " + (attempt + 1) + "/" + waits.length + " échouée : " + e);
                }
            }
        } finally {
            thread.setContextClassLoader(previous);
        }
        log.severe("[Acme] Impossible d'obtenir le certificat de " + s.domain() + (s.staging() ? " (staging)" : "") + ".");
        return false;
    }

    private void order(OvhDns dns, int waitSeconds) throws Exception {
        File acmeFolder = new File(dataFolder, "acme");
        Files.createDirectories(acmeFolder.toPath());

        Session session = new Session(s.staging() ? "acme://letsencrypt.org/staging" : "acme://letsencrypt.org");
        Account account = new AccountBuilder().agreeToTermsOfService().useKeyPair(accountKey(new File(acmeFolder, "account-" + (s.staging() ? "staging" : "prod") + ".key")))
                .addEmail(s.email()).create(session);

        Order order = account.newOrder().domain(s.domain()).create();
        for (Authorization auth : order.getAuthorizations()) {
            if (auth.getStatus() == Status.VALID) continue;
            Dns01Challenge challenge = auth.findChallenge(Dns01Challenge.class)
                    .orElseThrow(() -> new AcmeException("Let's Encrypt ne propose pas le défi DNS-01"));
            long recordId = dns.addTxt(s.challengeSubDomain(), challenge.getDigest());
            try {
                dns.refresh();
                log.info("[Acme] TXT " + s.challengeSubDomain() + "." + s.ovhZone() + " posé, attente de la propagation (" + waitSeconds + " s).");
                Thread.sleep(waitSeconds * 1000L);
                challenge.trigger();
                auth.waitForCompletion(Duration.ofMinutes(2));
                if (auth.getStatus() != Status.VALID) throw new AcmeException("Validation refusée : " + auth.getStatus());
            } finally {
                try {
                    dns.removeRecord(recordId);
                    dns.refresh();
                } catch (Exception e) {
                    log.warning("[Acme] TXT du défi non supprimé (id " + recordId + ") : " + e);
                }
            }
        }

        KeyPair domainKey = KeyPairUtils.createECKeyPair("secp256r1");
        CSRBuilder csr = new CSRBuilder();
        csr.addDomain(s.domain());
        csr.sign(domainKey);
        order.waitUntilReady(Duration.ofMinutes(1));
        order.execute(csr.getEncoded());
        order.waitForCompletion(Duration.ofMinutes(2));
        if (order.getStatus() != Status.VALID) throw new AcmeException("Commande non validée : " + order.getStatus());

        File newCert = new File(acmeFolder, "certificate.new");
        File newKey = new File(acmeFolder, "private.new");
        try (Writer w = Files.newBufferedWriter(newCert.toPath(), StandardCharsets.UTF_8)) {
            order.getCertificate().writeCertificate(w);
        }
        try (Writer w = Files.newBufferedWriter(newKey.toPath(), StandardCharsets.UTF_8)) {
            KeyPairUtils.writeKeyPair(domainKey, w);
        }
        backup(certFile, acmeFolder);
        backup(keyFile, acmeFolder);
        Files.move(newKey.toPath(), keyFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.move(newCert.toPath(), certFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        log.info("[Acme] Certificat de " + s.domain() + " obtenu" + (s.staging() ? " (STAGING : non reconnu par les navigateurs)" : "") + ".");
    }

    private static KeyPair accountKey(File file) throws IOException {
        if (file.isFile()) {
            try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                return KeyPairUtils.readKeyPair(r);
            }
        }
        KeyPair pair = KeyPairUtils.createECKeyPair("secp256r1");
        try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            KeyPairUtils.writeKeyPair(pair, w);
        }
        return pair;
    }

    private static void backup(File file, File folder) throws IOException {
        if (file.isFile()) Files.copy(file.toPath(), new File(folder, file.getName() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
