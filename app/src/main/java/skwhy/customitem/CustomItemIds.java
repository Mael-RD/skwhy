package skwhy.customitem;

import java.util.Locale;

/**
 * Identifiant d'un item custom : minuscules, espaces remplacés par {@code _}. C'est aussi le nom du fichier YAML et la
 * valeur du tag {@code custom_data;item} de l'item en jeu ({@code "joyau de préservation"} devient {@code joyau_de_préservation}).
 */
public final class CustomItemIds {

    private static final String FORBIDDEN = "/\\:*?\"<>|;";

    private CustomItemIds() {}

    public static String normalize(String id) {
        if (id == null) return "";
        return id.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
    }

    /** Un id vide, ou avec un caractère interdit dans un nom de fichier (ou {@code ;} des chemins NBT), est invalide. */
    public static boolean isValid(String normalized) {
        if (normalized == null || normalized.isEmpty() || normalized.startsWith(".")) return false;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c < 32 || FORBIDDEN.indexOf(c) >= 0) return false;
        }
        return true;
    }
}
