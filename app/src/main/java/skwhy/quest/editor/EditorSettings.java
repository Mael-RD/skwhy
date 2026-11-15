package skwhy.quest.editor;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import skwhy.SkWhy;
import skwhy.request.HttpHost;

/** Réglages de l'éditeur web, lus dans la section {@code quest_editor.*} de la config (chaque clé a une valeur par défaut). */
public final class EditorSettings {

    private EditorSettings() {}

    private static FileConfiguration config() {
        return SkWhy.getInstance().getConfig();
    }

    public static boolean enabled() {
        return config().getBoolean("quest_editor.enabled", true);
    }

    /** Durée de validité d'une clef (millisecondes), 2 h par défaut. */
    public static long keyTtlMillis() {
        return Math.max(1, config().getLong("quest_editor.key_ttl_minutes", 120)) * 60_000L;
    }

    /** Durée pendant laquelle une clef reste valide après la réponse du joueur, pour pouvoir la renouveler (5 min). */
    public static long renewMillis() {
        return Math.max(1, config().getLong("quest_editor.renew_minutes", 5)) * 60_000L;
    }

    /** Adresse publique du serveur (sans le chemin), ex. {@code https://play.exemple.fr:443}. Vide = déduite du port de l'API. */
    public static String baseUrl() {
        String configured = config().getString("quest_editor.public_url", "");
        int port = HttpHost.port();
        if (configured != null && !configured.isBlank()) {
            String url = configured.trim().replaceAll("/+$", "");
            // Le port d'écoute de l'API est toujours indiqué, sauf si l'adresse configurée en donne déjà un.
            return url.matches("(?i)^[a-z][a-z0-9+.-]*://[^/]*:\\d+(/.*)?$") ? url : url + ":" + port;
        }
        boolean https = HttpHost.isHttps();
        String host = Bukkit.getIp();
        if (host == null || host.isBlank() || host.equals("0.0.0.0")) host = "localhost";
        return (https ? "https://" : "http://") + host + ":" + port;
    }

    /** Dossier des modifications validées par un joueur absent (en attente de son retour). */
    public static File pendingFolder() {
        return new File(SkWhy.getInstance().getDataFolder(), config().getString("quest_editor.pending_folder", "quest-editor-pending"));
    }

    /** Dossier des sauvegardes des fichiers remplacés par l'éditeur. */
    public static File backupFolder() {
        return new File(SkWhy.getInstance().getDataFolder(), config().getString("quest_editor.backup_folder", "quest-editor-backups"));
    }

    /** Nombre de sauvegardes gardées par quête / NPC. */
    public static int backupsPerEntity() {
        return Math.max(0, config().getInt("quest_editor.backups_per_entity", 10));
    }
}
