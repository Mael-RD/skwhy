package skwhy.util;

import com.github.retrooper.packetevents.PacketEvents;

import skwhy.SkWhy;

/** Détection de PacketEvents (dépendance optionnelle) sans erreur de chargement de classe si le plugin est absent. */
public final class PacketEventsSupport {

    private PacketEventsSupport() {}

    /** {@code true} si PacketEvents est présent et initialisé ; sinon un avertissement nomme le module qui ne se charge pas. */
    public static boolean available(String moduleName) {
        boolean available;
        try {
            available = PacketEvents.getAPI() != null;
        } catch (LinkageError e) {
            available = false;
        }
        if (!available) {
            SkWhy.getInstance().getLogger().warning("[" + moduleName + "] PacketEvents est absent ou non initialisé : ce module n'est pas chargé.");
        }
        return available;
    }
}
