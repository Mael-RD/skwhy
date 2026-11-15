package skwhy.apiclient;

import java.util.List;
import java.util.Locale;

import org.bukkit.configuration.ConfigurationSection;

/** Réglages du client HTTP du module API (section {@code api_client} de la config). */
public record ApiClientSettings(
    int defaultTimeoutSeconds,
    long maxResponseBytes,
    int maxConcurrent,
    int maxRedirects,
    boolean allowPrivateAddresses,
    boolean allowInsecureCertificates,
    List<String> allowedHosts,
    List<String> blockedHosts,
    String userAgent
) {

    public static ApiClientSettings defaults() {
        return new ApiClientSettings(10, 10L * 1024 * 1024, 8, 5, true, false, List.of(), List.of(), "SkWhy");
    }

    /** Lit {@code api_client.*} ; une clé absente ou invalide garde sa valeur par défaut. */
    public static ApiClientSettings fromConfig(ConfigurationSection root, String pluginVersion) {
        ApiClientSettings d = defaults();
        ConfigurationSection s = root == null ? null : root.getConfigurationSection("api_client");
        if (s == null) return d.withUserAgent("SkWhy/" + pluginVersion);
        return new ApiClientSettings(
            Math.max(1, s.getInt("default_timeout_seconds", d.defaultTimeoutSeconds)),
            Math.max(1024L, (long) (s.getDouble("max_response_megabytes", d.maxResponseBytes / 1048576.0) * 1048576)),
            Math.max(1, s.getInt("max_concurrent", d.maxConcurrent)),
            Math.max(0, s.getInt("max_redirects", d.maxRedirects)),
            s.getBoolean("allow_private_addresses", d.allowPrivateAddresses),
            s.getBoolean("allow_insecure_certificates", d.allowInsecureCertificates),
            lower(s.getStringList("allowed_hosts")),
            lower(s.getStringList("blocked_hosts")),
            "SkWhy/" + pluginVersion
        );
    }

    private ApiClientSettings withUserAgent(String agent) {
        return new ApiClientSettings(defaultTimeoutSeconds, maxResponseBytes, maxConcurrent, maxRedirects,
            allowPrivateAddresses, allowInsecureCertificates, allowedHosts, blockedHosts, agent);
    }

    private static List<String> lower(List<String> values) {
        return values.stream().map(v -> v.trim().toLowerCase(Locale.ROOT)).filter(v -> !v.isEmpty()).toList();
    }
}
