package skwhy.quest.npc;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import skwhy.customentity.CustomEntity;
import skwhy.customentity.CustomEntityManager;
import skwhy.modules.CustomEntityElements.events.CustomEntityInteractEvent;
import skwhy.quest.model.NpcEntityRef;

/**
 * Clics sur un NPC qui est une entité custom, réelle ou fausse : le paquet de clic arrive sur le visuel, sur l'interaction fausse ou
 * sur l'interaction réelle, et le gestionnaire des entités custom le transforme en {@link CustomEntityInteractEvent}. C'est le seul
 * chemin pour ces entités (l'évènement Bukkit d'une interaction réelle est ignoré par {@code QuestListener} pour ne pas jouer deux fois).
 */
public final class CustomNpcListener implements Listener {

    @EventHandler
    public void onCustomClick(CustomEntityInteractEvent event) {
        CustomEntity custom = event.getEntity();
        NpcEntityRef ref = CustomNpcBodies.refOf(custom);
        if (ref == null) return;
        CustomEntityManager manager = CustomEntityManager.get();
        Entity real = custom.isReal() && manager != null ? manager.realEntity(custom) : null;
        // un clic gauche sur un corps custom fait réagir le NPC, comme sur un mannequin
        NpcService.interact(event.getPlayer(), real, ref, event.getClickType(), true);
    }
}
