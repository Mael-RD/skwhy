package skwhy.modules.Voice.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class VoicePhraseDetected extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String text;

    public VoicePhraseDetected(Player player, String text) {
        this.player = player;
        this.text = text;
    }

    public Player getPlayer() {
        return player;
    }

    public String getText() {
        return text;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
