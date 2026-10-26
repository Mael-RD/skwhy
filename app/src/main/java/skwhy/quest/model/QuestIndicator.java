package skwhy.quest.model;

/** Marqueur affiché au-dessus d'un NPC pour un joueur. Les index 0 à 4 correspondent aux anciens fichiers. */
public enum QuestIndicator {
    NEW("new", "<red><b>?"),
    MAIN("main", "<#ff8800><b>!"),
    SECONDARY("secondary", "<#44e1eb><b>!"),
    REGION("region", "<dark_purple>🗡"),
    INFO("info", "<#1cee13>🛈"),
    NONE("none", "");

    private final String id;
    private final String text;

    QuestIndicator(String id, String text) {
        this.id = id;
        this.text = text;
    }

    public String id() { return id; }

    /** Texte MiniMessage du marqueur. */
    public String text() { return text; }

    /** @return le marqueur d'index legacy (0 = new, 1 = main, 2 = secondary, 3 = region, 4 = info) ou {@link #NONE}. */
    public static QuestIndicator fromLegacy(int index) {
        return switch (index) {
            case 0 -> NEW;
            case 1 -> MAIN;
            case 2 -> SECONDARY;
            case 3 -> REGION;
            case 4 -> INFO;
            default -> NONE;
        };
    }

    public static QuestIndicator parse(String text) {
        if (text == null) return null;
        String t = text.trim().toLowerCase();
        for (QuestIndicator i : values()) if (i.id.equals(t)) return i;
        return switch (t) {
            case "second" -> SECONDARY;
            case "contrée", "contree", "country" -> REGION;
            default -> null;
        };
    }
}
