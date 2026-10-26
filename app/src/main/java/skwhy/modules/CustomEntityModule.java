package skwhy.modules;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;

import ch.njol.skript.lang.util.SimpleEvent;
import skwhy.SkWhy;
import skwhy.customentity.CustomEntity;
import skwhy.customentity.CustomEntityManager;
import skwhy.modules.CustomEntityElements.conditions.CondCustomEntityWalking;
import skwhy.modules.CustomEntityElements.effects.EffCustomEntity;
import skwhy.modules.CustomEntityElements.effects.EffCustomEntityWalk;
import skwhy.modules.CustomEntityElements.events.CustomEntityInteractEvent;
import skwhy.modules.CustomEntityElements.expressions.*;
import skwhy.modules.CustomEntityElements.sections.SecNewCustomEntity;
import skwhy.modules.CustomEntityElements.types.CustomEntityType;
import skwhy.util.PacketEventsSupport;

/**
 * Module d'entités custom : une définition (fichiers YAML du dossier {@code custom-entities}) décrit l'apparence ; l'entité réelle
 * (interaction) ou fausse (packets) ne porte que son id, et le plugin envoie le visuel par packet aux joueurs proches.
 */
public class CustomEntityModule implements AddonModule {

    @Override
    public String name() {
        return "CustomEntityModule";
    }

    @Override
    public boolean canLoad(SkriptAddon addon) {
        return PacketEventsSupport.available("CustomEntity");
    }

    @Override
    public void init(SkriptAddon addon) {
        CustomEntityType.register();
    }

    @Override
    public void load(SkriptAddon addon) {
        CustomEntityManager.init(SkWhy.getInstance());

        ExprNewCustomEntity.register(addon);
        ExprCustomEntityOf.register(addon);
        ExprRealEntityOf.register(addon);
        ExprCustomEntityId.register(addon);
        ExprCustomEntityLocation.register(addon);
        ExprCustomEntityIds.register(addon);
        ExprFakeCustomEntities.register(addon);
        ExprDefParamText.register(addon);
        ExprDefParamNumber.register(addon);
        ExprDefParamFlag.register(addon);
        ExprCustomEntityParam.register(addon);
        ExprCustomEntityOverrides.register(addon);
        SecNewCustomEntity.register(addon);
        EffCustomEntity.register(addon);
        EffCustomEntityWalk.register(addon);
        CondCustomEntityWalking.register(addon);

        this.moduleRegistry(addon).register(
            BukkitSyntaxInfos.Event.KEY,
            BukkitSyntaxInfos.Event.builder(SimpleEvent.class, "[Custom Entity] Custom Entity Interact")
                .addEvent(CustomEntityInteractEvent.class)
                .addPattern("custom entity interact")
                .addDescription("Fires when a player right-clicks (talk) or left-clicks (hit) a custom entity, real or fake, on its look or on its interaction. "
                    + "'event-player' is the player, 'event-customentity' the custom entity and 'event-clicktype' LEFT or RIGHT. "
                    + "The click on a fake custom entity, and on the look of a real one, never reaches the server otherwise.")
                .addExamples("on custom entity interact:", "\tif event-clicktype is right:", "\t\tsend \"Salut !\" to event-player")
                .addKeywords("custom", "entity", "interact", "click")
                .addSince("1.6.0")
                .build()
        );
        EventValueRegistry reg = addon.registry(EventValueRegistry.class);
        reg.register(EventValue.simple(CustomEntityInteractEvent.class, Player.class, CustomEntityInteractEvent::getPlayer));
        reg.register(EventValue.simple(CustomEntityInteractEvent.class, CustomEntity.class, CustomEntityInteractEvent::getEntity));
        reg.register(EventValue.simple(CustomEntityInteractEvent.class, ClickType.class, CustomEntityInteractEvent::getClickType));
    }

    /** À appeler depuis onDisable() du plugin principal. */
    public static void shutdown() {
        CustomEntityManager.shutdown();
    }
}
