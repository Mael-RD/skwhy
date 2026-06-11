package skwhy.modules;

import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import skwhy.modules.Voice.expressions.*;
import skwhy.modules.Voice.events.*;

import skwhy.voice.SpeechRecognizer;
import skwhy.voice.VoiceListener;

import skwhy.SkWhy;

import java.util.UUID;

public class VoiceModule implements AddonModule {

    private static VoiceListener voiceListener;
    private SpeechRecognizer speechRecognizer;

    @Override
    public String name() {
        return "VoiceModule";
    }

    @Override
    public boolean canLoad(SkriptAddon addon) {
        return true;
    }

    @Override
    public void init(SkriptAddon addon) {
        // types (none)
    }

    @Override
    public void load(SkriptAddon addon) {
        // Expressions
        VoiceRules.register(addon);
        VoiceTimeout.register(addon);

        // 1. Initialiser le moteur de reconnaissance
        speechRecognizer = new SpeechRecognizer(SkWhy.getInstance(), this::onPhraseDetected);
        if (!speechRecognizer.initialize()) {
            return;
        }

        // 2. Initialiser le listener VoiceChat
        voiceListener = new VoiceListener(SkWhy.getInstance(), speechRecognizer);
        voiceListener.register();
    }

    
    // -------------------------------------------------------------------------
    // Callback : phrase détectée → fire event Bukkit → Skript le capte
    // -------------------------------------------------------------------------

    public void onPhraseDetected(UUID playerId, String matchedPhrase, double confidence) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        // Restaurer la casse/accents originaux de la phrase (non normalisée)
        // Note : la phrase stockée dans Skript sera la version normalisée.
        // Pour retourner la phrase originale, stocker un Map normalise→original dans VoiceListener.
        // (optionnel, dépend du besoin)

        VoicePhraseDetected event = new VoicePhraseDetected(player, matchedPhrase);
        Bukkit.getPluginManager().callEvent(event);

        SkWhy.getInstance().getLogger().fine(String.format("[VoiceSkript] Phrase reconnue pour %s : \"%s\" (%.0f%%)",
                player.getName(), matchedPhrase, confidence * 100));
    }

    
    public static VoiceListener getVoiceListener() { return voiceListener; }
}
