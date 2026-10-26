package skwhy.quest;

import org.bukkit.Bukkit;

import skwhy.SkWhy;
import skwhy.quest.data.QuestStore;
import skwhy.quest.npc.CompassService;
import skwhy.quest.npc.DialogueService;
import skwhy.quest.npc.IndicatorService;

/** Cycle de vie du module de quêtes (chargement des définitions, services, écouteurs). */
public final class QuestManager {

    private static boolean started;

    private QuestManager() {}

    public static synchronized void init(SkWhy plugin) {
        if (started) return;
        started = true;
        QuestRegistry.reloadAll();
        QuestStore.start();
        Bukkit.getPluginManager().registerEvents(new QuestListener(), plugin);
        CompassService.start();
        IndicatorService.start();
        QuestCommand.register();
        plugin.getLogger().info("[Quest] " + QuestRegistry.quests().size() + " quête(s) et " + QuestRegistry.npcs().size() + " NPC chargés.");
    }

    /** Sauvegarde et arrête tout ; appelé automatiquement quand SkWhy est désactivé. */
    public static synchronized void shutdown() {
        if (!started) return;
        started = false;
        CompassService.shutdown();
        IndicatorService.shutdown();
        DialogueService.clearAll();
        QuestStore.shutdown();
    }
}
