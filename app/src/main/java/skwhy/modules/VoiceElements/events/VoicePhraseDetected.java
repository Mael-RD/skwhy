package skwhy.modules.VoiceElements.events;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.RequiredPlugins;
import ch.njol.skript.doc.Since;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

@Name("Voice Phrase Detected")
@Description("Fires when a voice phrase is recognized by Vosk for a player.")
@Examples({
    "on voice phrase detected:",
    "\tbroadcast \"%player% said: %event-string%\""
})
@Since("1.1.0")
@RequiredPlugins("SimpleVoiceChat")
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
