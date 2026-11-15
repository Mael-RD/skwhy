package skwhy.quest.editor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import skwhy.SkWhy;

/** Reconnexion d'un joueur : on lui redemande ce qu'il faut faire des modifications enregistrées pendant son absence. */
final class EditorListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(SkWhy.getInstance(), () -> {
            if (player.isOnline()) EditorService.promptPending(player);
        }, 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        EditorService.playerQuit(event.getPlayer().getUniqueId());
    }
}
