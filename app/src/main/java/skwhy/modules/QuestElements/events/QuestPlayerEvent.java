package skwhy.modules.QuestElements.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/**
 * Base des événements de quête. Le joueur peut être hors-ligne (progression modifiée par un staff, par un effet sur
 * un {@code offlineplayer}...) : {@link #getPlayer()} renvoie alors {@code null}.
 */
public abstract class QuestPlayerEvent extends Event {

    private final OfflinePlayer offlinePlayer;

    protected QuestPlayerEvent(OfflinePlayer player) {
        this.offlinePlayer = player;
    }

    public OfflinePlayer getOfflinePlayer() { return offlinePlayer; }

    /** Le joueur s'il est connecté, sinon {@code null}. */
    public Player getPlayer() { return offlinePlayer.getPlayer(); }
}
