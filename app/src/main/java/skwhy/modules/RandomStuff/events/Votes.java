package skwhy.modules.RandomStuff.events;

import com.vexsoftware.votifier.model.Vote;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class Votes extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Vote vote;

    public Votes(Vote vote) {
        this.vote = vote;
    }

    public Vote getVote()             { return vote; }
    public String getUsername()       { return vote.getUsername(); }
    public String getServiceName()    { return vote.getServiceName(); }
    public String getAddress()        { return vote.getAddress(); }
    public String getTimestamp()      { return vote.getTimeStamp(); }

    @Override public HandlerList getHandlers()          { return HANDLERS; }
    public static HandlerList getHandlerList()          { return HANDLERS; }
}