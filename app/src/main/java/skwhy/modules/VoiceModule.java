package skwhy.modules;

import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;

import org.skriptlang.skript.registration.SyntaxRegistry;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import ch.njol.skript.lang.util.SimpleEvent;

import skwhy.modules.Voice.expressions.*;
import skwhy.modules.Voice.events.*;

import skwhy.voice.SpeechRecognizer;
import skwhy.voice.VoiceListener;

import skwhy.SkWhy;

import java.io.File;
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
        if (!isVoskAvailable()) {
            SkWhy.getInstance().getLogger().warning("[VoiceSkript] Module voice désactivé : Vosk n'est pas disponible sur le serveur.");
            SkWhy.getInstance().getLogger().warning("[VoiceSkript] Ajoutez Vosk + JNA au classpath ou déployez un jar contenant ces dépendances.");
            return;
        }
        
        // Expressions
        VoiceRules.register(addon);

        // --- 1. ENREGISTREMENT DE LA SYNTAXE ---
        SyntaxRegistry syntaxRegistry = this.moduleRegistry(addon);
        
        syntaxRegistry.register(
            BukkitSyntaxInfos.Event.KEY,
            BukkitSyntaxInfos.Event.builder(SimpleEvent.class, "Voice Phrase Detected")
                .addEvent(VoicePhraseDetected.class) // <--- LA VRAIE MÉTHODE
                .addPattern("voice phrase [detected]") // (Méthode héritée de SyntaxInfo.Builder)
                .addDescription("Se déclenche quand une phrase vocale est reconnue par Vosk.") // <--- CORRIGÉ ICI AUSSI
                .build()
        );

        // --- 2. ENREGISTREMENT DES VALEURS ---
        EventValueRegistry valueRegistry = addon.registry(EventValueRegistry.class);
        
        valueRegistry.register(EventValue.simple(
            VoicePhraseDetected.class, 
            Player.class, 
            VoicePhraseDetected::getPlayer
        ));

        valueRegistry.register(EventValue.simple(
            VoicePhraseDetected.class, 
            String.class, 
            VoicePhraseDetected::getText
        ));

        try {
            // Assurer que le dossier de config et le dossier modèle existent
            ensureModelDirectory();

            // 1. Initialiser le moteur de reconnaissance
            speechRecognizer = new SpeechRecognizer(SkWhy.getInstance(), this::onPhraseDetected);
            if (!speechRecognizer.initialize()) {
                SkWhy.getInstance().getLogger().warning("[VoiceSkript] Module voice désactivé : impossible d'initialiser Vosk.");
                return;
            }

            // 2. Initialiser le listener VoiceChat
            voiceListener = new VoiceListener(SkWhy.getInstance(), speechRecognizer);
            if (!voiceListener.register()) {
                SkWhy.getInstance().getLogger().warning("[VoiceSkript] Module voice désactivé : SimpleVoiceChat introuvable.");
                return;
            }
        } catch (NoClassDefFoundError e) {
            SkWhy.getInstance().getLogger().severe("[VoiceSkript] Module voice désactivé : dépendance manquante -> " + e.getMessage());
            SkWhy.getInstance().getLogger().severe("[VoiceSkript] Vérifiez que Vosk + JNA sont fournis, et que SimpleVoiceChat est installé si vous utilisez les fonctionnalités voice.");
        } catch (Throwable t) {
            SkWhy.getInstance().getLogger().severe("[VoiceSkript] Erreur d'initialisation du module voice : " + t.getClass().getSimpleName() + " - " + t.getMessage());
            SkWhy.getInstance().getLogger().severe("[VoiceSkript] Le plugin continue sans le module voice.");
        }
    }

    private boolean isVoskAvailable() {
        try {
            Class.forName("org.vosk.LibVosk");
            Class.forName("org.vosk.LogLevel");
            Class.forName("org.vosk.Model");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
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
    }

    
    private void ensureModelDirectory() {
        File dataFolder = SkWhy.getInstance().getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        String modelFolderName = "model";
        try {
            if (SkWhy.getInstance().getConfig() != null) {
                modelFolderName = SkWhy.getInstance().getConfig().getString("voice.model", modelFolderName);
            }
        } catch (Exception ignored) {
        }

        File modelDir = new File(dataFolder, modelFolderName);
        if (!modelDir.exists()) {
            if (modelDir.mkdirs()) {
                SkWhy.getInstance().getLogger().info("[VoiceSkript] Dossier de modèle créé : " + modelDir.getAbsolutePath());
            } else {
                SkWhy.getInstance().getLogger().warning("[VoiceSkript] Impossible de créer le dossier de modèle : " + modelDir.getAbsolutePath());
            }
        }
    }

    public static VoiceListener getVoiceListener() { return voiceListener; }
}
