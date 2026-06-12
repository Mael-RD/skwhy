package skwhy.voice;

import org.vosk.Model;
import org.vosk.Recognizer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Session de reconnaissance vocale streaming à liste stricte.
 *
 * ── Principe général ──────────────────────────────────────────────────────────
 *
 * Vosk écoute en continu avec une grammaire restreinte exacte.
 * Si les règles sont ["utilise soin", "utilise feu"], Vosk n'écoutera
 * RIEN D'AUTRE.
 *
 * Si le joueur dit un mot au hasard, bégaie, ou ne finit pas sa phrase,
 * Vosk renvoie "[unk]" (que l'on ignore silencieusement).
 * Si le joueur dit la phrase parfaite, Skript est immédiatement prévenu.
 * Aucun risque de blocage ou de désynchronisation.
 */
public class StreamingSpeechSession {

    private static final int AUDIO_QUEUE_CAPACITY = 300;
    private static final long PHRASE_COOLDOWN_MS = 1200; // Évite que "utilise soin" s'active 2x en 1 seconde

    private final UUID playerId;
    private final Model voskModel;
    private final float sampleRate;
    private final MatchCallback callback;

    /** Règles actuellement actives */
    private volatile List<String> activeRules;

    /** Règles à activer après le prochain déclenchement */
    private volatile List<String> nextRules = null;

    // ── Recognizer actif (protégé par recognizerLock) ─────────────────────────
    private Recognizer currentRecognizer;
    private final Object recognizerLock = new Object();

    // ── File audio et Threading ───────────────────────────────────────────────
    private final BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>(AUDIO_QUEUE_CAPACITY);
    private final Thread workerThread;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final Map<String, Long> lastFireTime = new HashMap<>();

    public StreamingSpeechSession(UUID playerId,
                                  List<String> initialRules,
                                  Model voskModel,
                                  float sampleRate,
                                  MatchCallback callback) throws IOException {
        this.playerId = playerId;
        this.activeRules = new ArrayList<>(initialRules);
        this.voskModel = voskModel;
        this.sampleRate = sampleRate;
        this.callback = callback;

        // Création du modèle avec la liste exacte
        currentRecognizer = buildRecognizer(activeRules);
        
        workerThread = new Thread(this::processLoop, "VoiceSkript-" + playerId);
        workerThread.setDaemon(true);
        workerThread.start();
    }

    // ── API publique ──────────────────────────────────────────────────────────

    public void feedAudio(byte[] pcm16k) {
        if (running.get()) {
            audioQueue.offer(pcm16k);
        }
    }

    public void close() {
        running.set(false);
        workerThread.interrupt();
        
        // Sécurité anti-crash JNI : on attend max 500ms que le thread se termine
        try {
            workerThread.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        synchronized (recognizerLock) {
            if (currentRecognizer != null) {
                currentRecognizer.close();
            }
        }
    }

    public void setNextRules(List<String> rules) {
        this.nextRules = new ArrayList<>(rules);
    }

    public void replaceRules(List<String> rules) throws IOException {
        synchronized (recognizerLock) {
            nextRules = null;
            activeRules = new ArrayList<>(rules);
            hotswap(activeRules);
        }
    }
    
    public List<String> getRawPhrases() {
        return new ArrayList<>(activeRules);
    }

    // ── Boucle principale ─────────────────────────────────────────────────────

    private void processLoop() {
        try {
            while (running.get()) {
                byte[] chunk = audioQueue.poll(150, TimeUnit.MILLISECONDS);
                if (chunk == null) continue;

                List<byte[]> batch = new ArrayList<>();
                batch.add(chunk);
                audioQueue.drainTo(batch, 15);

                for (byte[] c : batch) {
                    processChunk(c);
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
        String jsonToParse = null;
        boolean isPartial = false;

        synchronized (recognizerLock) {
            hasResult = currentRecognizer.acceptWaveForm(chunk, chunk.length);
            if (hasResult) {
                // Résultat final (le joueur a fait un silence)
                jsonToParse = currentRecognizer.getResult();
            } else {
                // Résultat partiel (le joueur est en train de parler)
                jsonToParse = currentRecognizer.getPartialResult();
                isPartial = true;
            }
        }

        if (jsonToParse != null) {
            // On extrait le texte selon s'il est partiel ou final
            String text = isPartial ? extractPartial(jsonToParse) : extractText(jsonToParse);

            if (!text.isEmpty() && !text.equals("[unk]")) {
                for (String rule : activeRules) {
                    // Toujours ton filtre strict : n [unk] au début, et c'est tout.
                    String pattern = "^(\\[unk\\]\\s*)*" + java.util.regex.Pattern.quote(rule) + "$";
                    
                    if (text.matches(pattern)) {
                        fireResult(rule); // Déclenchement immédiat !
                        
                        // ─── LE SECRET DE L'INTERRUPTION IMMÉDIATE ───
                        if (isPartial) {
                            synchronized (recognizerLock) {
                                // On force Vosk à oublier le reste de la phrase 
                                // et on le remet à 0 instantanément.
                                currentRecognizer.reset(); 
                            }
                        }
                        // ─────────────────────────────────────────────
                        break;
                    }
                }
            }
        }
    }

    // ── Déclenchement ─────────────────────────────────────────────────────────

    private void fireResult(String result) throws IOException {
        long now = System.currentTimeMillis();
        Long last = lastFireTime.get(result);
        if (last != null && now - last < PHRASE_COOLDOWN_MS) {
            return; // Anti-spam activé
        }
        lastFireTime.put(result, now);

        // Déclenche l'event Skript !
        callback.onMatch(playerId, result, 1.0);

        // Si Skript a demandé à changer les règles suite à cet event
        List<String> next = nextRules;
        nextRules = null;

        if (next != null) {
            synchronized (recognizerLock) {
                activeRules = next;
                hotswap(activeRules);
            }
        }
    }

    // ── Hotswap et Création du Recognizer ─────────────────────────────────────

    private void hotswap(List<String> rules) throws IOException {
        Recognizer newRec = buildRecognizer(rules);
        Recognizer old;
        synchronized (recognizerLock) {
            old = currentRecognizer;
            currentRecognizer = newRec;
        }
        if (old != null) old.close();
    }

    private Recognizer buildRecognizer(List<String> rules) throws IOException {
        if (rules.isEmpty()) {
            return new Recognizer(voskModel, sampleRate);
        }
        
        // On construit la syntaxe JSON stricte que Vosk exige: ["phrase 1", "phrase 2", "[unk]"]
        StringBuilder sb = new StringBuilder("[");
        for (String rule : rules) {
            sb.append("\"").append(rule.toLowerCase().trim()).append("\", ");
        }
        sb.append("\"[unk]\"]"); // Toujours ajouter [unk] pour absorber le bruit
        
        return new Recognizer(voskModel, sampleRate, sb.toString());
    }

    // ── Extraction JSON ───────────────────────────────────────────────────────

    private String extractText(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("text")) {
                return o.get("text").getAsString().toLowerCase().trim(); 
                // Si tu avais une classe TextNormalizer, tu peux la remettre ici : 
                // return TextNormalizer.normalize(o.get("text").getAsString());
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String extractPartial(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("partial")) {
                return o.get("partial").getAsString().toLowerCase().trim();
            }
        } catch (Exception ignored) {}
        return "";
    }

    @FunctionalInterface
    public interface MatchCallback {
        void onMatch(UUID playerId, String result, double confidence);
    }
}