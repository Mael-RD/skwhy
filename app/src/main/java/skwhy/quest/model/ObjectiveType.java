package skwhy.quest.model;

public enum ObjectiveType {
    /** Avancé uniquement par un script (action {@code quest substep}, effet {@code progress objective}...). */
    EXTERNAL,
    /** Avancé par {@code trigger quest action "<clé>"} (ex. {@code break.stone}). */
    ACTION,
    /** Nombre d'exemplaires d'un item dans l'inventaire. */
    ITEM,
    /** Vérifié par le moteur : métier, quête, variable, succès, rang. */
    SPECIAL;

    public static ObjectiveType parse(String text) {
        if (text == null) return EXTERNAL;
        return switch (text.trim().toLowerCase()) {
            case "action" -> ACTION;
            case "item" -> ITEM;
            case "special", "spécial" -> SPECIAL;
            default -> EXTERNAL;
        };
    }
}
