package skwhy.quest.model;

/** Statut d'une quête pour un joueur. {@link #UNKNOWN} : la quête n'a jamais été débloquée. */
public enum QuestStatus {
    UNKNOWN("unknown", null),
    AVAILABLE("available", "disponible"),
    STARTED("started", "commencée"),
    FINISHED("finished", "terminée");

    private final String id;
    private final String legacy;

    QuestStatus(String id, String legacy) {
        this.id = id;
        this.legacy = legacy;
    }

    public String id() { return id; }

    /** Nom utilisé par les anciens fichiers joueur (français). */
    public String legacyName() { return legacy; }

    public static QuestStatus parse(String text) {
        if (text == null) return null;
        String t = text.trim().toLowerCase();
        for (QuestStatus s : values()) {
            if (s.id.equals(t) || (s.legacy != null && s.legacy.equals(t))) return s;
        }
        return switch (t) {
            case "start", "in progress" -> STARTED;
            case "finish", "complete", "completed", "done" -> FINISHED;
            case "new" -> AVAILABLE;
            default -> null;
        };
    }
}
