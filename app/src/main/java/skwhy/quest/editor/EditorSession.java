package skwhy.quest.editor;

import java.util.UUID;

/** Une clef d'éditeur : ce qu'elle ouvre, pour quel joueur, jusqu'à quand, et où en sont les modifications envoyées. */
public final class EditorSession {

    public enum State {
        /** Le navigateur peut modifier. */
        OPEN,
        /** Les modifications sont envoyées, le joueur doit les valider ou les annuler en jeu. */
        SUBMITTED,
        APPLIED,
        CANCELLED
    }

    final String id = UUID.randomUUID().toString();
    final String keyHash;
    final String kind;
    final String targetId;
    final UUID player;
    final String playerName;
    final long createdAt;

    volatile long expiresAt;
    volatile State state = State.OPEN;
    /** Nouvelle clef quand le joueur a renouvelé le lien (le navigateur s'y redirige). */
    volatile String renewedKey;
    volatile ChangeSet changes;

    EditorSession(String keyHash, String kind, String targetId, UUID player, String playerName, long createdAt, long expiresAt) {
        this.keyHash = keyHash;
        this.kind = kind;
        this.targetId = targetId;
        this.player = player;
        this.playerName = playerName;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String id() { return id; }
    public String kind() { return kind; }
    public String targetId() { return targetId; }
    public UUID player() { return player; }
    public State state() { return state; }
    public long expiresAt() { return expiresAt; }
    public String renewedKey() { return renewedKey; }
}
