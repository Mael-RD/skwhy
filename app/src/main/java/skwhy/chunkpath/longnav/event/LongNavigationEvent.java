package skwhy.chunkpath.longnav.event;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import skwhy.chunkpath.longnav.LongNavigation;

/**
 * Fin d'une long navigation (arrivée, échec, abandon). Valable pour toute navigation : entité réelle, entité réelle
 * pilotée virtuellement ou entité fictive (par id). Déclenché depuis le thread principal.
 */
public abstract class LongNavigationEvent extends Event {

    private final LongNavigation navigation;
    private final Entity entity;
    private final List<Player> viewers;
    private final Location location;

    protected LongNavigationEvent(LongNavigation navigation) {
        this.navigation = navigation;
        this.entity = navigation.getEntity();
        this.viewers = List.copyOf(navigation.getPlayers());
        this.location = navigation.getCurrentLocation();
    }

    public LongNavigation getNavigation() { return navigation; }
    /** L'entité Bukkit de la navigation ; {@code null} pour une entité fictive (créée par id). */
    public Entity getEntity() { return entity; }
    /** Les joueurs qui voyaient la navigation au moment de l'événement (vide pour une entité réelle pilotée). */
    public List<Player> getViewers() { return viewers; }
    /** Où se trouvait l'entité à ce moment. */
    public Location getLocation() { return location; }

    @Override public abstract HandlerList getHandlers();

    /** Destination atteinte. */
    public static final class Arrive extends LongNavigationEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        public Arrive(LongNavigation navigation) { super(navigation); }
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    /** Aucune route trouvée (chunks non enregistrés, départ ou arrivée non marchables...). */
    public static final class Fail extends LongNavigationEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        public Fail(LongNavigation navigation) { super(navigation); }
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    /** Le dernier joueur qui voyait la navigation s'est déconnecté, a changé de monde ou s'est trop éloigné : elle s'arrête. */
    public static final class Abandon extends LongNavigationEvent {
        private static final HandlerList HANDLERS = new HandlerList();
        public Abandon(LongNavigation navigation) { super(navigation); }
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }
}
