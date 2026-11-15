package skwhy.chunkpath.longnav;

import org.bukkit.configuration.file.FileConfiguration;

/** Réglages des long navigations, lus dans la section {@code long_navigation.*} de la config de SkWhy. */
public final class LongNavSettings {

    /** Valeur par défaut de {@code long_navigation.sync_every_moves}. */
    public static final int DEFAULT_SYNC_EVERY_MOVES = 20;

    /** Autorise plusieurs navigations sur une même entité (sinon la sécurité {@link NavigationGuard} s'applique). */
    public static volatile boolean allowMultiple = false;
    /** Une navigation virtuelle envoie une téléportation absolue tous les N paquets de déplacement relatif. */
    public static volatile int syncEveryMoves = DEFAULT_SYNC_EVERY_MOVES;

    private LongNavSettings() {}

    public static void load(FileConfiguration config) {
        allowMultiple = config.getBoolean("long_navigation.allow_multiple", false);
        syncEveryMoves = Math.max(1, config.getInt("long_navigation.sync_every_moves", DEFAULT_SYNC_EVERY_MOVES));
    }
}
