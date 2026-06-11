package skwhy.voice;

import java.util.List;
import java.util.ArrayList;

/**
 * Règle de déclenchement conditionnel.
 *
 * Une règle relie un TRIGGER à un PAYLOAD :
 *   trigger  = phrase/mot qui arme l'écoute  (ex : "utilise")
 *   payload  = phrase/mot réellement écouté  (ex : "attaque")
 *   result   = valeur reportée dans l'event  (ex : "attaque" — peut différer du payload)
 *   windowMs = durée (ms) pendant laquelle le payload doit être prononcé après le trigger
 *
 * On distingue deux modes :
 *
 *  MODE SIMPLE   — trigger seul, pas de payload.
 *                  L'event se déclenche dès que le trigger est reconnu.
 *                  C'est le comportement de la V1 (liste plate).
 *
 *  MODE CHAINED  — trigger + payload.
 *                  1. On détecte le trigger dans le flux → on arme.
 *                  2. Dans la fenêtre windowMs, on cherche le payload.
 *                  3. Si trouvé → event avec result.
 *                  4. Si la fenêtre expire sans payload → désarmement silencieux.
 *
 * Plusieurs payloads peuvent être associés au même trigger :
 *   "utilise" → ["attaque", "soin", "bouclier"]
 * Dans ce cas, créer une TriggerRule par couple (trigger, payload).
 */
public class TriggerRule {

    public enum Mode { SIMPLE, CHAINED }

    /** Phrase/mot qui active l'écoute (normalisé) */
    public final String trigger;

    /** Phrase/mot à détecter après le trigger (normalisé). Null en mode SIMPLE. */
    public final String payload;

    /**
     * Valeur renvoyée dans l'event Skript.
     * En mode SIMPLE  → égal au trigger.
     * En mode CHAINED → défini par l'utilisateur (souvent = payload, mais peut être custom).
     */
    public final String result;

    /** Fenêtre d'écoute du payload après détection du trigger (ms). */
    public final long windowMs;

    public final Mode mode;

    // ── Constructeur mode SIMPLE ──────────────────────────────────────────────

    public TriggerRule(String trigger) {
        this.result   = trigger;
        this.trigger  = TextNormalizer.normalize(trigger);
        this.payload  = null;
        this.windowMs = 0;
        this.mode     = Mode.SIMPLE;
    }

    // ── Constructeur mode CHAINED ─────────────────────────────────────────────

    /**
     * @param trigger   Mot déclencheur (ex : "utilise")
     * @param payload   Mot/phrase à écouter après (ex : "attaque")
     * @param result    Valeur de l'event (ex : "attaque") — null → utilise payload
     * @param windowMs  Fenêtre d'écoute en ms (ex : 3000)
     */
    public TriggerRule(String trigger, String payload, long windowMs) {
        this.result   = trigger;
        this.trigger  = TextNormalizer.normalize(trigger);
        this.payload  = TextNormalizer.normalize(payload);
        this.windowMs = windowMs;
        this.mode     = Mode.CHAINED;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isChained() { return mode == Mode.CHAINED; }

    @Override
    public String toString() {
        if (mode == Mode.SIMPLE) return "SIMPLE[" + trigger + "]";
        return "CHAINED[" + trigger + " → " + payload + " (" + windowMs + "ms)]";
    }

    // ── Builder utilitaire pour l'API Skript ──────────────────────────────────

    public static class Builder {
        private final String trigger;
        private final List<TriggerRule> rules = new ArrayList<>();
        private long defaultWindowMs = 3000;

        public Builder(String trigger) { this.trigger = trigger; }
        public Builder window(long ms) { this.defaultWindowMs = ms; return this; }

        /** Ajoute un payload simple (result = payload) */
        public Builder then(String payload) {
            rules.add(new TriggerRule(trigger, payload, defaultWindowMs));
            return this;
        }

        public List<TriggerRule> build() { return rules; }
    }
}
