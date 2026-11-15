package skwhy.apiclient;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

/** Contrôle de l'URL d'une requête sortante : schéma, liste blanche / noire, adresses privées. */
public final class HostGuard {

    private HostGuard() {}

    /** @return {@code null} si la requête est permise, sinon le motif du refus. */
    public static String check(URI uri, ApiClientSettings settings) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return "unsupported scheme '" + scheme + "' (http or https expected)";
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) return "the url has no host";
        host = host.toLowerCase(Locale.ROOT);
        if (matches(host, settings.blockedHosts())) return "host '" + host + "' is blocked by api_client.blocked_hosts";
        if (!settings.allowedHosts().isEmpty() && !matches(host, settings.allowedHosts())) {
            return "host '" + host + "' is not in api_client.allowed_hosts";
        }
        if (!settings.allowPrivateAddresses()) {
            try {
                for (InetAddress address : InetAddress.getAllByName(host)) {
                    if (isPrivate(address)) {
                        return "host '" + host + "' resolves to a private address (api_client.allow_private_addresses is false)";
                    }
                }
            } catch (UnknownHostException e) {
                return "unknown host '" + host + "'";
            }
        }
        return null;
    }

    /** Un hôte correspond à une entrée s'il est identique ou en est un sous-domaine. */
    static boolean matches(String host, List<String> entries) {
        for (String entry : entries) {
            if (host.equals(entry) || host.endsWith("." + entry)) return true;
        }
        return false;
    }

    static boolean isPrivate(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] raw = a.getAddress();
        // fc00::/7 (unique local IPv6)
        return raw.length == 16 && (raw[0] & 0xfe) == 0xfc;
    }
}
