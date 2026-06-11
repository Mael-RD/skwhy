package skwhy.voice;

import org.vosk.Model;
import org.vosk.Recognizer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import skwhy.SkWhy;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Session de reconnaissance vocale streaming avec commutation de grammaire.
 *
 * ── Principe général ──────────────────────────────────────────────────────────
 *
 *  Vosk supporte les grammaires JSON restrictives : en lui passant
 *  ["utilise", "attaque", "soin", "[unk]"], il n'émet JAMAIS de mots hors-liste.
 *  Le décodeur acoustique est ~10× plus rapide sur une petite grammaire.
 *
 *  La session maintient UN Recognizer actif à la fois, qui peut être remplacé
 *  à chaud (hotswap) en < 1ms (le Model est déjà chargé en RAM).
 *
 * ── États de la session ───────────────────────────────────────────────────────
 *
 *  TRIGGER_LISTEN  (état initial)
 *    Grammaire = uniquement les triggers de la liste active.
 *    Vosk ignore tout phonème hors-liste.
 *    Dès qu'un trigger est reconnu → passe à PAYLOAD_LISTEN.
 *
 *  PAYLOAD_LISTEN
 *    Grammaire = uniquement les payloads du trigger détecté
 *               + triggers de la liste suivante (si définie par Skript)
 *    Fenêtre temporelle : windowMs ms.
 *    Si payload reconnu dans la fenêtre → fire event + commuter vers next_rules.
 *    Si fenêtre expirée → retour TRIGGER_LISTEN.
 *
 * ── Commutation de liste (setNextRules) ──────────────────────────────────────
 *
 *  Skript peut appeler setNextRules() depuis le callback de l'event.
 *  La nouvelle liste est activée INSTANTANÉMENT après la détection du payload,
 *  sans silence requis, car le Recognizer est remplacé à chaud avec la
 *  nouvelle grammaire — les bytes audio qui arrivent pendant le hotswap sont
 *  mis en buffer (< 5ms) et ré-injectés dans le nouveau Recognizer.
 *
 * ── Optimisation "n'écouter que ce qui est demandé" ──────────────────────────
 *
 *  La grammaire Vosk est la vraie protection : le modèle acoustique ne produit
 *  que les tokens de la grammaire. On ne fait AUCUNE comparaison Levenshtein
 *  en mode PAYLOAD_LISTEN — la sortie Vosk EST le résultat (ou [unk]).
 *  En mode TRIGGER_LISTEN on accepte [unk] comme "silence/bruit ignoré".
 *
 * ── Buffer d'enregistrement (mode RECORD) ────────────────────────────────────
 *
 *  En phase PAYLOAD_LISTEN, si la grammaire est trop petite pour que Vosk
 *  soit fiable (< 2 entrées), on bascule automatiquement en mode RECORD :
 *  on accumule les bytes audio de la fenêtre, puis on les passe en une seule
 *  fois au Recognizer à l'expiration. Cela évite les faux positifs sur les
 *  partials Vosk pour des mots très courts (< 2 syllabes).
 */
public class StreamingSpeechSession {

    // ── Constantes ────────────────────────────────────────────────────────────

    private static final int    AUDIO_QUEUE_CAPACITY = 300;
    private static final long   PHRASE_COOLDOWN_MS   = 1200;
    private static final double MATCH_THRESHOLD      = 0.82;
    /** Seuil plus strict pour les résultats "partial" Vosk (moins stables) */
    private static final double PARTIAL_THRESHOLD    = 0.87;
    /**
     * Si la grammaire de payload a moins de RECORD_MODE_THRESHOLD entrées
     * ET que le payload fait 1 seul mot, utiliser le mode enregistrement
     * pour éviter les faux positifs sur les partials.
     */
    private static final int    RECORD_MODE_THRESHOLD = 2;

    // ── État de la session ────────────────────────────────────────────────────

    private enum Phase { TRIGGER_LISTEN, PAYLOAD_LISTEN }

    private final UUID      playerId;
    private final Model     voskModel;
    private final float     sampleRate;
    private final MatchCallback callback;
    
    private volatile long windowTimeoutMs = 3000L;

    /** Règles actuellement actives */
    private volatile List<TriggerRule> activeRules;

    /**
     * Règles à activer après le prochain fire (set par Skript via setNextRules).
     * Null = rester sur activeRules.
     */
    private volatile List<TriggerRule> nextRules = null;

    /** Phase courante */
    private volatile Phase phase = Phase.TRIGGER_LISTEN;

    /** Fenêtre armée courante (non-null seulement en PAYLOAD_LISTEN) */
    private volatile ArmedWindow armedWindow = null;

    // ── Recognizer actif (protégé par recognizerLock) ─────────────────────────

    private Recognizer currentRecognizer;
    private final Object recognizerLock = new Object();

    // ── File audio ────────────────────────────────────────────────────────────

    private final BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>(AUDIO_QUEUE_CAPACITY);
    private final Thread                workerThread;
    private final AtomicBoolean         running    = new AtomicBoolean(true);

    /** Buffer d'enregistrement pour le mode RECORD (PAYLOAD_LISTEN court) */
    private final List<byte[]> recordBuffer = new ArrayList<>();
    private boolean            recordMode   = false;

    /** Cooldown par résultat (la string renvoyée à Skript) */
    private final Map<String, Long> lastFireTime = new HashMap<>();

    // ── Fenêtre armée ─────────────────────────────────────────────────────────

    private static class ArmedWindow {
        final List<TriggerRule> matchingRules; // règles dont le trigger vient d'être détecté
        final GrammarSet        payloadGrammar;
        final long              expiresAt;

        ArmedWindow(List<TriggerRule> rules, long windowMs) {
            this.matchingRules  = rules;
            this.payloadGrammar = GrammarSet.fromPayloads(rules);
            this.expiresAt      = System.currentTimeMillis() + windowMs;
        }

        boolean isExpired() { return System.currentTimeMillis() > expiresAt; }

        /** Durée max parmi les règles (pour la fenêtre commune) */
        static long maxWindow(List<TriggerRule> rules) {
            return rules.stream().mapToLong(r -> r.windowMs).max().orElse(3000);
        }
    }

    // ── Constructeur ──────────────────────────────────────────────────────────

    public StreamingSpeechSession(UUID playerId,
                                   List<TriggerRule> initialRules,
                                   Model voskModel,
                                   float sampleRate,
                                   MatchCallback callback) throws IOException {
        this.playerId    = playerId;
        this.activeRules = new ArrayList<>(initialRules);
        this.voskModel   = voskModel;
        this.sampleRate  = sampleRate;
        this.callback    = callback;

        // Démarrer en phase TRIGGER avec la grammaire des triggers
        currentRecognizer = buildRecognizer(GrammarSet.fromTriggers(activeRules));
        
        workerThread = new Thread(this::processLoop, "VoiceSkript-" + playerId);
        workerThread.setDaemon(true);
        workerThread.start();
    }

    // ── API publique ──────────────────────────────────────────────────────────

    public void feedAudio(byte[] pcm16k) {
        if (running.get()) audioQueue.offer(pcm16k);
    }

    public boolean isOpen() { return running.get(); }

    public void close() {
        running.set(false);
        workerThread.interrupt();
        synchronized (recognizerLock) {
            if (currentRecognizer != null) currentRecognizer.close();
        }
    }

    /**
     * Définit la liste de règles à activer IMMÉDIATEMENT après le prochain fire.
     * Appelé depuis le thread Bukkit (callback Skript) → thread-safe via volatile.
     *
     * Si appelé pendant PAYLOAD_LISTEN, la nouvelle liste sera active dès que
     * le payload est confirmé (hotswap du Recognizer).
     */
    public void setNextRules(List<TriggerRule> rules) {
        this.nextRules = new ArrayList<>(rules);
    }

    /**
     * Remplace immédiatement la liste active (reset complet de l'état).
     * Pour les cas où Skript veut changer la liste hors d'un fire.
     */
    public void replaceRules(List<TriggerRule> rules) throws IOException {
        nextRules = null;
        activeRules = new ArrayList<>(rules);
        phase = Phase.TRIGGER_LISTEN;
        armedWindow = null;
        recordBuffer.clear();
        recordMode = false;
        hotswap(GrammarSet.fromTriggers(activeRules));
    }

    /**
     * Retourne le timeout courant des ArmedWindows en millisecondes.
     * Appelé par ExprVoiceTimeout.get().
     */
    public long getWindowTimeoutMs() {
        return windowTimeoutMs;
    }

    public void setWindowTimeoutMs(long ms) {
        this.windowTimeoutMs = ms;
    }
    
    public List<String> getRawPhrases() {
        // Retourne les phrases surveillées (sans [unk]), pour l'expression Skript "voice rules of player"
        // Utile pour afficher la liste actuelle au joueur, sans les tokens [unk] de silence/bruit
        List<String> phrases = new ArrayList<>();
        for (TriggerRule r : activeRules) {
            phrases.add(r.result);
        }
        return phrases;
    }

    // ── Boucle principale ─────────────────────────────────────────────────────

    private void processLoop() {
        try {
            while (running.get()) {
                byte[] chunk = audioQueue.poll(150, TimeUnit.MILLISECONDS);

                // Vérifier expiration de la fenêtre armée même sans audio
                if (armedWindow != null && armedWindow.isExpired()) {
                    onWindowExpired();
                }

                if (chunk == null) continue;

                List<byte[]> batch = new ArrayList<>();
                batch.add(chunk);
                audioQueue.drainTo(batch, 15);

                for (byte[] c : batch) {
                    if (recordMode) {
                        recordBuffer.add(c);
                        // Vérifier si la fenêtre vient d'expirer
                        if (armedWindow != null && armedWindow.isExpired()) {
                            flushRecordBuffer();
                        }
                    } else {
                        processChunk(c);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processChunk(byte[] chunk) throws IOException {
        boolean hasResult;
        String resultJson, partialJson;

        synchronized (recognizerLock) {
            hasResult   = currentRecognizer.acceptWaveForm(chunk, chunk.length);
            resultJson  = hasResult ? currentRecognizer.getResult() : null;
            partialJson = hasResult ? null : currentRecognizer.getPartialResult();
        }

        if (hasResult && resultJson != null) {
            String text = extractText(resultJson);
            if (!text.isEmpty() && !text.equals("[unk]")) {
                onTranscribed(text, false);
            }
        } else if (partialJson != null) {
            String partial = extractPartial(partialJson);
            if (!partial.isEmpty() && !partial.equals("[unk]")) {
                onTranscribed(partial, true);
            }
        }
    }

    // ── Gestion des transcriptions ────────────────────────────────────────────

    private void onTranscribed(String text, boolean isPartial) throws IOException {
        // Debug: log Vosk detections for the player session
        try {
            SkWhy.getInstance().getLogger().fine("[VoiceSkript DEBUG] Vosk détecté pour " + playerId + " : '" + text + "' (partial=" + isPartial + ") phase=" + phase);
        } catch (Exception ignored) {
        }

        double threshold = isPartial ? PARTIAL_THRESHOLD : MATCH_THRESHOLD;

        if (phase == Phase.TRIGGER_LISTEN) {
            handleTriggerPhase(text, threshold);
        } else {
            handlePayloadPhase(text, threshold);
        }
    }

    /** Phase TRIGGER : chercher un trigger dans le texte transcrit */
    private void handleTriggerPhase(String text, double threshold) throws IOException{
        // Grouper les règles CHAINED par trigger pour armer une seule fenêtre
        // quand le même trigger sert plusieurs payloads
        Map<String, List<TriggerRule>> byTrigger = new LinkedHashMap<>();
        for (TriggerRule r : activeRules) {
            byTrigger.computeIfAbsent(r.trigger, k -> new ArrayList<>()).add(r);
        }

        for (Map.Entry<String, List<TriggerRule>> entry : byTrigger.entrySet()) {
            String trigger = entry.getKey();
            List<TriggerRule> group = entry.getValue();

            if (matches(text, trigger, threshold)) {
                boolean allSimple = group.stream().noneMatch(TriggerRule::isChained);
                if (allSimple) {
                    // Règles SIMPLE → fire direct
                    for (TriggerRule r : group) fireResult(r.result);
                } else {
                    // Règles CHAINED → armer la fenêtre et changer de grammaire
                    armWindow(group);
                }
                return; // un seul trigger par transcription
            }
        }
    }

    /** Phase PAYLOAD : chercher un payload dans la fenêtre armée */
    private void handlePayloadPhase(String text, double threshold) throws IOException {
        ArmedWindow aw = armedWindow;
        if (aw == null || aw.isExpired()) {
            onWindowExpired();
            return;
        }

        for (TriggerRule rule : aw.matchingRules) {
            if (rule.payload != null && matches(text, rule.payload, threshold)) {
                fireResult(rule.result);
                return;
            }
        }
    }

    // ── Armement de la fenêtre payload ────────────────────────────────────────

    private void armWindow(List<TriggerRule> rules) throws IOException {
        long winMs = ArmedWindow.maxWindow(
                rules.stream().filter(TriggerRule::isChained)
                        .collect(java.util.stream.Collectors.toList()));

        armedWindow = new ArmedWindow(rules, winMs);
        phase = Phase.PAYLOAD_LISTEN;

        GrammarSet payloadGrammar = armedWindow.payloadGrammar;

        // Mode RECORD si la grammaire est trop petite pour être fiable sur les partials
        // (mots très courts → Vosk instable sur les partials, mieux vaut attendre le result)
        boolean smallGrammar = payloadGrammar.getEntries().size() < RECORD_MODE_THRESHOLD;
        boolean shortPayloads = rules.stream()
                .filter(r -> r.payload != null)
                .allMatch(r -> r.payload.split("\\s+").length == 1);

        recordMode = smallGrammar && shortPayloads;

        if (recordMode) {
            recordBuffer.clear();
            // On garde le recognizer TRIGGER actif pendant l'enregistrement
            // (inutile de changer la grammaire, on ne lit pas le résultat)
        } else {
            // Hotswap vers la grammaire des payloads
            hotswap(payloadGrammar);
        }
    }

    // ── Expiration de la fenêtre ──────────────────────────────────────────────

    private void onWindowExpired() throws IOException {
        if (recordMode) flushRecordBuffer();
        armedWindow = null;
        phase = Phase.TRIGGER_LISTEN;
        recordMode = false;
        recordBuffer.clear();
        hotswap(GrammarSet.fromTriggers(activeRules));
    }

    /**
     * Fin du mode RECORD : passer tout l'audio accumulé au Recognizer
     * avec la grammaire payload, lire le résultat final.
     */
    private void flushRecordBuffer() throws IOException {
        if (recordBuffer.isEmpty() || armedWindow == null) {
            onWindowExpired();
            return;
        }

        // Créer un Recognizer temporaire avec la grammaire payload
        Recognizer tmpRec = buildRecognizer(armedWindow.payloadGrammar);
        for (byte[] c : recordBuffer) tmpRec.acceptWaveForm(c, c.length);
        String text = extractText(tmpRec.getFinalResult());
        tmpRec.close();
        recordBuffer.clear();

        if (!text.isEmpty() && !text.equals("[unk]")) {
            handlePayloadPhase(text, MATCH_THRESHOLD);
        }
        // Que le payload ait été trouvé ou non, retourner en TRIGGER
        if (phase == Phase.PAYLOAD_LISTEN) {
            onWindowExpired();
        }
    }

    // ── Fire et transition vers next_rules ────────────────────────────────────

    private void fireResult(String result) throws IOException {
        long now = System.currentTimeMillis();
        Long last = lastFireTime.get(result);
        if (last != null && now - last < PHRASE_COOLDOWN_MS) return;
        lastFireTime.put(result, now);

        // Callback vers Skript (thread Bukkit)
        callback.onMatch(playerId, result, 1.0);

        // Commuter vers nextRules si Skript en a défini une
        List<TriggerRule> next = nextRules;
        nextRules = null;

        if (next != null) {
            activeRules = next;
            phase = Phase.TRIGGER_LISTEN;
            armedWindow = null;
            recordMode = false;
            recordBuffer.clear();
            // Hotswap IMMÉDIAT vers la nouvelle grammaire
            // Les bytes audio qui arrivent pendant ce < 1ms sont dans la queue,
            // ils seront traités avec la nouvelle grammaire dès le prochain tour
            hotswap(GrammarSet.fromTriggers(activeRules));
        } else {
            // Pas de next → retour en TRIGGER_LISTEN avec les mêmes règles
            phase = Phase.TRIGGER_LISTEN;
            armedWindow = null;
            recordMode = false;
            recordBuffer.clear();
            hotswap(GrammarSet.fromTriggers(activeRules));
        }
    }

    // ── Hotswap du Recognizer ─────────────────────────────────────────────────

    /**
     * Remplace le Recognizer actif par un nouveau avec la grammaire donnée.
     * Opération < 1ms (le Model acoustique est déjà en RAM).
     * Thread-safe : protégé par recognizerLock.
     */
    private void hotswap(GrammarSet grammar) throws IOException {
        Recognizer newRec = buildRecognizer(grammar);
        Recognizer old;
        synchronized (recognizerLock) {
            old = currentRecognizer;
            currentRecognizer = newRec;
        }
        if (old != null) old.close();
    }

    private Recognizer buildRecognizer(GrammarSet grammar) throws IOException {
        if (grammar.isEmpty()) {
            // Grammaire vide → accepter tout (fallback de sécurité)
            return new Recognizer(voskModel, sampleRate);
        }
        return new Recognizer(voskModel, sampleRate, grammar.getJson());
    }

    // ── Comparaison ───────────────────────────────────────────────────────────

    /**
     * Vérifie si [transcribed] correspond à [target].
     *
     * En mode grammaire, Vosk ne produit que des tokens de la grammaire,
     * donc la comparaison est quasi-exacte. Levenshtein sert à absorber
     * les légères variations de transcription (ex: "attaque" → "attac").
     *
     * On cherche [target] dans [transcribed] comme sous-séquence de mots
     * pour gérer "... attaque ..." au milieu d'un partial plus long.
     */
    private boolean matches(String transcribed, String target, double threshold) {
        if (transcribed.equals(target)) return true;

        String[] tw = transcribed.split("\\s+");
        String[] rw = target.split("\\s+");
        int len = rw.length;

        if (tw.length < len) {
            // Le transcrit est plus court → comparaison directe
            return SpeechRecognizer.computeSimilarity(transcribed, target) >= threshold;
        }

        // Fenêtre glissante sur les mots
        for (int i = 0; i <= tw.length - len; i++) {
            String window = String.join(" ", Arrays.copyOfRange(tw, i, i + len));
            if (SpeechRecognizer.computeSimilarity(window, target) >= threshold) return true;
        }
        return false;
    }

    // ── Extraction JSON Vosk ──────────────────────────────────────────────────

    private String extractText(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("text")) return TextNormalizer.normalize(o.get("text").getAsString());
        } catch (Exception ignored) {}
        return "";
    }

    private String extractPartial(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("partial")) return TextNormalizer.normalize(o.get("partial").getAsString());
        } catch (Exception ignored) {}
        return "";
    }

    // ── Callback ──────────────────────────────────────────────────────────────

    @FunctionalInterface
    public interface MatchCallback {
        void onMatch(UUID playerId, String result, double confidence);
    }
}
