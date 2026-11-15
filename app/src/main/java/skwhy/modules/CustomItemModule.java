package skwhy.modules;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;

import ch.njol.skript.lang.util.SimpleEvent;

import skwhy.SkWhy;
import skwhy.customitem.CustomItemManager;
import skwhy.customitem.events.CustomItemEvent;
import skwhy.modules.CustomItemElements.conditions.CondCustomItem;
import skwhy.modules.CustomItemElements.effects.EffCustomItem;
import skwhy.modules.CustomItemElements.expressions.ExprCustomItem;
import skwhy.modules.CustomItemElements.expressions.ExprCustomItemInfo;
import skwhy.modules.CustomItemElements.expressions.ExprCustomItemTools;

/**
 * Module d'items custom : une définition par fichier YAML ({@code custom-items/<id>.yml}) décrit l'item (matériau, nom,
 * composants). Le plugin gère les définitions, le NBT de l'item en jeu et les événements ; les actions des items restent en Skript.
 */
public class CustomItemModule implements AddonModule {

    @Override
    public String name() {
        return "CustomItemModule";
    }

    @Override
    public void load(SkriptAddon addon) {
        CustomItemManager.init(SkWhy.getInstance());

        // l'expression d'info avant la creation : 'custom item id of x' ne doit pas etre lu comme 'custom item <texte>'
        ExprCustomItemInfo.register(addon);
        ExprCustomItemTools.register(addon);
        ExprCustomItem.register(addon);
        EffCustomItem.register(addon);
        CondCustomItem.register(addon);

        registerEvent(addon, CustomItemEvent.Click.class, "Click", "custom item click",
            "A player left-clicks or right-clicks (in the air or on a block) with a custom item in hand. 'event-clicktype' is LEFT, RIGHT, SHIFT_LEFT or SHIFT_RIGHT and 'event-block' the clicked block, if any. "
                + "Cancelling it cancels the click (nothing is placed or used).",
            "on custom item click:", "	if custom item id of event-item is \"sceptre\":", "		cancel event", "		send \"Zap !\" to event-player");
        registerEvent(addon, CustomItemEvent.InventoryClick.class, "Inventory Click", "custom item inventory click",
            "A player clicks in an inventory on a custom item, or with a custom item on the cursor ('event-item' is the clicked item, else the cursor). 'event-clicktype' is the click type. Cancelling it cancels the click.",
            "on custom item inventory click:", "	if \"safe\" is contained in custom item parameters of event-item:", "		cancel event");
        registerEvent(addon, CustomItemEvent.Drop.class, "Drop", "custom item drop",
            "A player drops a custom item. Cancelling it keeps the item.",
            "on custom item drop:", "	if \"safe\" is contained in custom item parameters of event-item:", "		cancel event");
        registerEvent(addon, CustomItemEvent.Pickup.class, "Pickup", "custom item pickup",
            "A player is about to pick up a custom item from the ground. Cancelling it leaves the item on the ground.",
            "on custom item pickup:", "	if \"owner\" is contained in custom item parameters of event-item:", "		cancel event");
        registerEvent(addon, CustomItemEvent.Consume.class, "Consume", "custom item consume",
            "A player eats or drinks a custom item. Cancelling it keeps the item and its effects away.",
            "on custom item consume:", "	send \"Miam !\" to event-player");
        registerEvent(addon, CustomItemEvent.Place.class, "Place", "custom item place",
            "A player places a custom item as a block; 'event-block' is the placed block. Cancelling it cancels the placement.",
            "on custom item place:", "	cancel event");
        registerEvent(addon, CustomItemEvent.Held.class, "Held", "custom item held",
            "A player selects a custom item in the hotbar (it becomes the item in hand).",
            "on custom item held:", "	send \"En main : %custom item id of event-item%\" to event-player");
        registerEvent(addon, CustomItemEvent.Unheld.class, "Unheld", "custom item unheld",
            "A player selects another hotbar slot while holding a custom item ('event-item' is the item put away).",
            "on custom item unheld:", "	send \"Range : %custom item id of event-item%\" to event-player");
        registerEvent(addon, CustomItemEvent.Equip.class, "Equip", "custom item equip",
            "A player puts a custom item in an armor slot ('event-item' is the item now worn). It cannot be cancelled.",
            "on custom item equip:", "	send \"Equipe : %custom item id of event-item%\" to event-player");
        registerEvent(addon, CustomItemEvent.Unequip.class, "Unequip", "custom item unequip",
            "A player removes a custom item from an armor slot ('event-item' is the item that was worn). It cannot be cancelled.",
            "on custom item unequip:", "	send \"Retire : %custom item id of event-item%\" to event-player");

        EventValueRegistry values = addon.registry(EventValueRegistry.class);
        values.register(EventValue.simple(CustomItemEvent.class, Player.class, CustomItemEvent::getPlayer));
        values.register(EventValue.simple(CustomItemEvent.class, ItemStack.class, CustomItemEvent::getItem));
        values.register(EventValue.simple(CustomItemEvent.class, ClickType.class, CustomItemEvent::getClickType));
        values.register(EventValue.simple(CustomItemEvent.class, Block.class, CustomItemEvent::getBlock));
    }


    private void registerEvent(SkriptAddon addon, Class<? extends CustomItemEvent> type, String title, String pattern, String description, String... examples) {
        this.moduleRegistry(addon).register(
            BukkitSyntaxInfos.Event.KEY,
            BukkitSyntaxInfos.Event.builder(SimpleEvent.class, "[Custom Item] Custom Item " + title)
                .addEvent(type)
                .addPattern(pattern)
                .addDescription(description + " 'event-player' is the player and 'event-item' the custom item (its id: 'custom item id of event-item'). Only items with a custom item id fire it.")
                .addExamples(examples)
                .addKeywords("custom", "item", title.toLowerCase())
                .addSince("1.6.0")
                .build()
        );
    }

    /** À appeler depuis onDisable() du plugin principal. */
    public static void shutdown() {
        CustomItemManager.shutdown();
    }
}
