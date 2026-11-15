package skwhy.modules.ApiElements;

import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import skwhy.SkWhy;
import skwhy.apiclient.ApiCall;
import skwhy.apiclient.ApiClient;
import skwhy.apiclient.ApiClientSettings;
import skwhy.apiclient.ApiResponse;

/** Point d'entrée des syntaxes Skript vers le client HTTP : réglages relus à chaque envoi, réponse rendue sur le thread principal. */
public final class ApiClientService {

    private static ApiClient client;

    private ApiClientService() {}

    public static ApiClientSettings settings() {
        SkWhy plugin = SkWhy.getInstance();
        return ApiClientSettings.fromConfig(plugin.getConfig(), plugin.getPluginMeta().getVersion());
    }

    public static synchronized void start() {
        if (client == null) client = new ApiClient(settings().maxConcurrent());
    }

    public static synchronized void stop() {
        if (client != null) client.shutdown();
        client = null;
    }

    /**
     * Envoie la requête sans bloquer ; {@code onResponse} s'exécute sur le thread principal avec la réponse
     * (jamais {@code null} : un échec réseau est une réponse de statut 0).
     *
     * @param timeoutMillis délai qui remplace celui de la requête, ou {@code null}
     */
    public static void send(ApiCall call, @Nullable Long timeoutMillis, Consumer<ApiResponse> onResponse) {
        ApiClient c;
        synchronized (ApiClientService.class) {
            start();
            c = client;
        }
        ApiCall copy = call.copy();
        if (timeoutMillis != null) copy.timeoutMillis(timeoutMillis);
        SkWhy plugin = SkWhy.getInstance();
        c.sendAsync(copy, settings()).thenAccept(response -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> onResponse.accept(response));
        });
    }
}
