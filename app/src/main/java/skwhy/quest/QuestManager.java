package skwhy.quest;

import org.bukkit.Bukkit;

import skwhy.SkWhy;
import skwhy.quest.data.QuestStore;
import skwhy.quest.npc.CompassService;
import skwhy.quest.npc.CustomNpcBodies;
import skwhy.quest.npc.CustomNpcListener;
import skwhy.quest.npc.NpcPlaces;
import skwhy.quest.npc.NpcService;
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
        NpcPlaces.load();
        QuestStore.start();
        Bukkit.getPluginManager().registerEvents(new QuestListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new CustomNpcListener(), plugin);
        CustomNpcBodies.install();
        // les entités déjà chargées (rechargement du plugin) : sans cela leur tag n'est vu qu'au prochain chargement de chunk
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (org.bukkit.entity.Entity entity : world.getEntities()) QuestRegistry.onLoad(entity);
            }
            NpcService.refreshAll();
        });
        CompassService.start();
        IndicatorService.start();
        QuestCommand.register();
        skwhy.quest.editor.EditorService.start();
        plugin.getLogger().info("[Quest] " + QuestRegistry.quests().size() + " quête(s) et " + QuestRegistry.npcs().size() + " NPC chargés.");
    }

    /** Sauvegarde et arrête tout ; appelé automatiquement quand SkWhy est désactivé. */
    public static synchronized void shutdown() {
        if (!started) return;
        started = false;
        skwhy.quest.editor.EditorService.stop();
        CustomNpcBodies.uninstall();
        CompassService.shutdown();
        IndicatorService.shutdown();
        DialogueService.clearAll();
        QuestStore.shutdown();
    }
}
