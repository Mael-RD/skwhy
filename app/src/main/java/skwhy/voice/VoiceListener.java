package skwhy.voice;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;

/**
 * Pont entre SimpleVoiceChat et les sessions de reconnaissance de chaque joueur.
 * Accepte désormais des TriggerRule (simples ou chaînées) à la place d'une liste plate.
 */
public class VoiceListener implements VoicechatPlugin {

    private static final String PLUGIN_ID = "SkWhy";

    private final JavaPlugin plugin;
    private final SpeechRecognizer recognizer;

    private final Map<UUID, StreamingSpeechSession> sessions  = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>>      listenedRules  = new ConcurrentHashMap<>();
    
    private VoicechatApi voicechatApi;
    @Override
    public void initialize(VoicechatApi api) {
        this.voicechatApi = api;
    }

    public VoicechatApi getApi() {
        return voicechatApi;
    }

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

        StreamingSpeechSession s = sessions.get(id);
        if (s == null) return;

        // 1. On crée un décodeur via l'API de SimpleVoiceChat
        OpusDecoder decoder = voicechatApi.createDecoder();
        
        // 2. On décode les données Opus en PCM 48kHz (tableau de shorts)
        short[] decodedPcm48k = decoder.decode(event.getPacket().getOpusEncodedData());
        decoder.close(); // Important pour éviter les fuites de mémoire
        
        // 3. On doit convertir ces shorts en octets 16kHz pour Vosk
        byte[] pcm16k = resampleAndConvert48kTo16k(decodedPcm48k);

        s.feedAudio(pcm16k);
    }
    // ── API Skript ────────────────────────────────────────────────────────────

    /**
     * Écoute SIMPLE : liste de phrases.
     */
    public void startListening(Player player, List<String> rules) throws IOException {
        UUID id = player.getUniqueId();

        StreamingSpeechSession old = sessions.remove(id);
        if (old != null) {
            old.close();
        }

        List<String> copyRules = new ArrayList<>(rules);
        listenedRules.put(id, copyRules);
        StreamingSpeechSession session = recognizer.openSession(id, copyRules);
        sessions.put(id, session);
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

    private byte[] resampleAndConvert48kTo16k(short[] pcm48k) {
        // On divise par 3 pour passer de 48kHz à 16kHz
        int outLength = pcm48k.length / 3;
        // 2 octets par short
        byte[] result = new byte[outLength * 2]; 
        
        for (int i = 0; i < outLength; i++) {
            // On prend 1 échantillon sur 3
            short sample = pcm48k[i * 3]; 
            
            // On convertit le short (16 bits) en 2 bytes (Little Endian, requis par la plupart des systèmes Vosk)
            result[i * 2] = (byte) (sample & 0xff);
            result[(i * 2) + 1] = (byte) ((sample >> 8) & 0xff);
        }
        return result;
    }

    /**
     * Retourne la session active d'un joueur, ou null s'il n'est pas sur écoute.
     * Utilisé par les effets Skript EffSetNextVoiceRules / EffReplaceVoiceRules.
     */
    public StreamingSpeechSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

}