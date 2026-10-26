package skwhy.modules.QuestElements.events;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Cancellable;
import skwhy.quest.model.Quest;

public class QuestStepChangeEvent extends QuestPlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Quest quest;
    private final int pastStep;
    private final int step;

    private boolean cancelled;

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    public QuestStepChangeEvent(OfflinePlayer player, Quest quest, int pastStep, int step) {
        super(player);
        this.quest = quest;
        this.pastStep = pastStep;
        this.step = step;
    }

    public Quest getQuest() { return quest; }

    public int getPastStep() { return pastStep; }

    public int getStep() { return step; }

    /** Étape précédente en 1-based (0 : la quête n'était pas commencée). */
    public int getPastStepNumber() { return pastStep + 1; }

    /** Nouvelle étape en 1-based (0 : la quête est réinitialisée ; au-delà de la dernière étape : terminée). */
    public int getStepNumber() { return step + 1; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
