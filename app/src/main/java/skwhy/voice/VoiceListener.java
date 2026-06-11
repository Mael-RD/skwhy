package skwhy.voice;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Pont entre SimpleVoiceChat et les sessions de reconnaissance de chaque joueur.
 * Accepte désormais des TriggerRule (simples ou chaînées) à la place d'une liste plate.
 */
public class VoiceListener implements VoicechatPlugin {

    private static final String PLUGIN_ID = "voiceskript-addon";

    private final JavaPlugin plugin;
    private final SpeechRecognizer recognizer;

    private final Map<UUID, StreamingSpeechSession> sessions       = new ConcurrentHashMap<>();
    private final Map<UUID, List<TriggerRule>>      listenedRules  = new ConcurrentHashMap<>();

    public VoiceListener(JavaPlugin plugin, SpeechRecognizer recognizer) {
        this.plugin     = plugin;
        this.recognizer = recognizer;
    }

    // ── Enregistrement VoiceChat ──────────────────────────────────────────────

    public boolean register() {
        BukkitVoicechatService service = plugin.getServer()
                .getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            plugin.getLogger().severe("[VoiceSkript] SimpleVoiceChat introuvable !");
            return false;
        }
        service.registerPlugin(this);
        plugin.getLogger().info("[VoiceSkript] VoiceListener enregistré.");
        return true;
    }

    @Override public String getPluginId() { return PLUGIN_ID; }

    @Override
    public void registerEvents(EventRegistration reg) {
        reg.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
    }

    // ── Capture audio ─────────────────────────────────────────────────────────

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        if (event.getSenderConnection() == null) return;
        VoicechatConnection senderConnection = event.getSenderConnection();
        if (senderConnection == null) return;
        UUID id = senderConnection.getPlayer().getUuid();
        if (!listenedRules.containsKey(id)) return;

        byte[] pcm16k = resample48kTo16k(event.getPacket().getOpusEncodedData());
        StreamingSpeechSession s = sessions.get(id);
        if (s != null && s.isOpen()) {
            // Debug : log when audio is forwarded to Vosk for a listening player
            Player bp = Bukkit.getPlayer(id);
            String name = bp != null ? bp.getName() : id.toString();
            plugin.getLogger().fine("[VoiceSkript DEBUG] Envoi audio à Vosk pour " + name + " (" + id + ") — bytes=" + pcm16k.length);
            s.feedAudio(pcm16k);
        }
    }

    // ── API Skript ────────────────────────────────────────────────────────────

    /**
     * Écoute SIMPLE (rétrocompatibilité) : liste de phrases sans trigger.
     * Chaque phrase est une TriggerRule en mode SIMPLE.
     */
    public void startListening(Player player, List<String> phrases) throws IOException {
        List<TriggerRule> rules = new ArrayList<>();
        for (String p : phrases) rules.add(new TriggerRule(p));
        startListeningWithRules(player, rules);
    }

    /**
     * Écoute avec règles (mode trigger→payload inclus).
     */
    public void startListeningWithRules(Player player, List<TriggerRule> rules) throws IOException {
        UUID id = player.getUniqueId();

        StreamingSpeechSession old = sessions.remove(id);
        if (old != null) old.close();

        listenedRules.put(id, rules);
        StreamingSpeechSession session = recognizer.openSession(id, rules);
        sessions.put(id, session);

        plugin.getLogger().info("[VoiceSkript] Écoute démarrée pour "
                + player.getName() + " — " + rules.size() + " règle(s)");
    }

    public void stopListening(Player player) {
        UUID id = player.getUniqueId();
        listenedRules.remove(id);
        StreamingSpeechSession s = sessions.remove(id);
        if (s != null) s.close();
    }

    public boolean isListening(Player player) {
        return listenedRules.containsKey(player.getUniqueId());
    }

    // ── Rééchantillonnage 48kHz → 16kHz ──────────────────────────────────────

    private byte[] resample48kTo16k(byte[] pcm48k) {
        int out = (pcm48k.length / 2) / 3;
        byte[] result = new byte[out * 2];
        for (int i = 0; i < out; i++) {
            int src = i * 6;
            if (src + 1 < pcm48k.length) {
                result[i*2]   = pcm48k[src];
                result[i*2+1] = pcm48k[src+1];
            }
        }
        return result;
    }

    /**
     * Ajoute une règle à un joueur déjà sur écoute sans réinitialiser sa session.
     * Si le joueur n'est pas encore sur écoute, ouvre une session avec cette unique règle.
     */
    public void addRule(Player player, TriggerRule rule) {
        UUID id = player.getUniqueId();
        List<TriggerRule> existing = listenedRules.computeIfAbsent(id, k -> new ArrayList<>());
        existing.add(rule);

        // Recréer la session avec la liste mise à jour
        StreamingSpeechSession old = sessions.remove(id);
        if (old != null) old.close();

        try {
            StreamingSpeechSession session = recognizer.openSession(id, existing);
            sessions.put(id, session);
        } catch (IOException e) {
            plugin.getLogger().severe("[VoiceSkript] Erreur lors de l'ajout de la règle : " + e.getMessage());
        }
    }

    /**
     * Retourne la session active d'un joueur, ou null s'il n'est pas sur écoute.
     * Utilisé par les effets Skript EffSetNextVoiceRules / EffReplaceVoiceRules.
     */
    public StreamingSpeechSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

}