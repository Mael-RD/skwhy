package skwhy.quest.editor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Les clefs d'éditeur valides. Une clef est un secret de 256 bits tiré au hasard ; seule son empreinte SHA-256 est gardée.
 * Une clef inconnue, expirée ou invalide donne le même résultat ({@code null}) : l'appelant ferme la connexion sans rien dire.
 */
public final class EditorSessions {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, EditorSession> byHash = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public EditorSessions() {
        this(System::currentTimeMillis);
    }

    public EditorSessions(LongSupplier clock) {
        this.clock = clock;
    }

    /** Résultat d'une création : la clef en clair (à ne donner qu'au joueur) et sa session. */
    public record Issued(String key, EditorSession session) {}

    public Issued issue(String kind, String targetId, UUID player, String playerName, long ttlMillis) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long now = clock.getAsLong();
        EditorSession session = new EditorSession(hash(key), kind, targetId, player, playerName, now, now + ttlMillis);
        byHash.put(session.keyHash, session);
        return new Issued(key, session);
    }

    /** @return la session de cette clef, ou {@code null} si elle est inconnue ou expirée */
    public EditorSession find(String key) {
        if (key == null || key.length() < 20 || key.length() > 100) return null;
        EditorSession session = byHash.get(hash(key));
        if (session == null) return null;
        if (session.expiresAt <= clock.getAsLong()) {
            byHash.remove(session.keyHash);
            return null;
        }
        return session;
    }

    public EditorSession findById(String sessionId) {
        for (EditorSession s : byHash.values()) if (s.id.equals(sessionId) && s.expiresAt > clock.getAsLong()) return s;
        return null;
    }

    public void invalidate(EditorSession session) {
        if (session != null) byHash.remove(session.keyHash);
    }

    /** Retire les clefs expirées. */
    public void purge() {
        long now = clock.getAsLong();
        byHash.values().removeIf(s -> s.expiresAt <= now);
    }

    public int size() {
        return byHash.size();
    }

    public void clear() {
        byHash.clear();
    }

    public java.util.Collection<EditorSession> all() {
        return java.util.List.copyOf(byHash.values());
    }

    public long now() {
        return clock.getAsLong();
    }

    static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
