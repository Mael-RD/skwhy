package skwhy.voice;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Fabrique de StreamingSpeechSession.
 * Le Model Vosk est partagé (thread-safe en lecture).
 * Chaque joueur a son propre Recognizer (stateful).
 */
public class SpeechRecognizer {

    private final JavaPlugin       plugin;
    private       Model            voskModel;
    private final PhraseMatchCallback globalCallback;

    private static final float VOSK_SAMPLE_RATE = 16000f;

    public SpeechRecognizer(JavaPlugin plugin, PhraseMatchCallback globalCallback) {
        this.plugin         = plugin;
        this.globalCallback = globalCallback;
    }

    // ── Init / shutdown ───────────────────────────────────────────────────────

    public boolean initialize() {
        File modelDir = new File(plugin.getDataFolder(), "model");
        if (!modelDir.exists() || !modelDir.isDirectory()) {
            plugin.getLogger().severe("[VoiceSkript] Modèle Vosk absent dans plugins/VoiceSkript/model/");
            plugin.getLogger().severe("[VoiceSkript] → vosk-model-small-fr-0.22 sur alphacephei.com/vosk/models");
            return false;
        }
        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS);
            voskModel = new Model(modelDir.getAbsolutePath());
            plugin.getLogger().info("[VoiceSkript] Modèle Vosk chargé.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("[VoiceSkript] Erreur Vosk : " + e.getMessage());
            return false;
        }
    }

    public void shutdown() {
        if (voskModel != null) voskModel.close();
    }

    public Model getModel() { return voskModel; }

    // ── Ouverture de session ──────────────────────────────────────────────────

    public StreamingSpeechSession openSession(UUID playerId, List<TriggerRule> rules) throws IOException {
        try {
            return new StreamingSpeechSession(
                    playerId, rules, voskModel, VOSK_SAMPLE_RATE,
                    (pid, result, confidence) ->
                            Bukkit.getScheduler().runTask(plugin, () ->
                                    globalCallback.onPhraseDetected(pid, result, confidence))
            );
        } catch (IOException e) {
            throw new IOException("Erreur lors de l'ouverture de la session", e);
        }
    }

    // ── Similarité statique (utilisée par StreamingSpeechSession.matches) ─────

    public static double computeSimilarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        return 0.70 * levenshteinSimilarity(a, b) + 0.30 * phoneticSimilarity(a, b);
    }

    private static double levenshteinSimilarity(String a, String b) {
        return 1.0 - (double) levenshteinDistance(a, b) / Math.max(a.length(), b.length());
    }

    private static int levenshteinDistance(String a, String b) {
        int la = a.length(), lb = b.length();
        int[][] dp = new int[la + 1][lb + 1];
        for (int i = 0; i <= la; i++) dp[i][0] = i;
        for (int j = 0; j <= lb; j++) dp[0][j] = j;
        for (int i = 1; i <= la; i++)
            for (int j = 1; j <= lb; j++) {
                int c = a.charAt(i-1) == b.charAt(j-1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i-1][j]+1, dp[i][j-1]+1), dp[i-1][j-1]+c);
            }
        return dp[la][lb];
    }

    private static double phoneticSimilarity(String a, String b) {
        String[] aw = a.split("\\s+"), bw = b.split("\\s+");
        if (aw.length == 0 || bw.length == 0) return 0.0;
        int m = 0;
        for (String t : aw) {
            String ts = soundexFr(t);
            for (String r : bw) if (ts.equals(soundexFr(r))) { m++; break; }
        }
        return (double) m / Math.max(aw.length, bw.length);
    }

    static String soundexFr(String word) {
        if (word == null || word.isEmpty()) return "";
        String w = word.toLowerCase()
                .replaceAll("ph","f").replaceAll("eau|au","o")
                .replaceAll("ai|ei|ay","e").replaceAll("ou","u")
                .replaceAll("eu","e").replaceAll("qu","k")
                .replaceAll("ch","s").replaceAll("gn","n")
                .replaceAll("th","t").replaceAll("[àâ]","a")
                .replaceAll("[éèêë]","e").replaceAll("[îï]","i")
                .replaceAll("[ôö]","o").replaceAll("[ùûü]","u")
                .replaceAll("[^a-z]","");
        if (w.isEmpty()) return "";
        int[] t = new int[26];
        for (char c : "bfpv".toCharArray())    t[c-'a'] = 1;
        for (char c : "cgjkqsxz".toCharArray()) t[c-'a'] = 2;
        for (char c : "dt".toCharArray())       t[c-'a'] = 3;
        t['l'-'a'] = 4;
        for (char c : "mn".toCharArray())       t[c-'a'] = 5;
        t['r'-'a'] = 6;
        StringBuilder code = new StringBuilder();
        code.append(Character.toUpperCase(w.charAt(0)));
        int prev = t[w.charAt(0)-'a'];
        for (int i = 1; i < w.length() && code.length() < 4; i++) {
            char c = w.charAt(i);
            if (c < 'a' || c > 'z') continue;
            int cur = t[c-'a'];
            if (cur != 0 && cur != prev) code.append(cur);
            prev = cur;
        }
        while (code.length() < 4) code.append('0');
        return code.toString();
    }

    @FunctionalInterface
    public interface PhraseMatchCallback {
        void onPhraseDetected(UUID playerId, String result, double confidence);
    }
}
