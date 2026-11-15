package skwhy.modules.CustomItemElements.effects;

import java.util.Map;

import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import skwhy.customitem.CustomItemCounter;
import skwhy.customitem.CustomItemManager;
import skwhy.customitem.CustomItemUpdater;
import skwhy.customitem.ItemCodec;
import skwhy.quest.QuestDefinitions;

@Name("[Custom Item] Save / Delete / Reload / Update / Consume")
@Description("'save <item> as custom item <id>' writes the item as the file custom-items/<id>.yml (name, lore and every component are kept; the tags 'item' and 'owner' of the item are not): " +
    "the version goes up by one when the content changed ('force save' always raises it), which updates the items already given when they are next updated. " +
    "'save custom item definition <id> from <text>' writes a definition given as SNBT text. Nothing is written for an invalid id or definition. " +
    "'delete custom item <id>' removes its file. 'reload custom items' reads the folder again. " +
    "'update custom items of <inventory>' replaces each custom item of the inventory with its 'updated custom item'. " +
    "'consume <n> of custom item <id> from <inventory>' removes up to n items of that id.")
@Examples({
    "save tool of player as custom item \"epee_de_feu\"",
    "force save tool of player as custom item \"epee_de_feu\"",
    "save custom item definition \"pomme\" from \"{material_id:\"\"apple\"\",name:\"\"<red>Pomme\"\"}\"",
    "delete custom item \"epee_de_feu\"",
    "reload custom items",
    "update custom items of inventory of player",
    "consume 2 of custom item \"billet\" from inventory of player"
})
@Since("1.6.0")
public class EffCustomItem extends Effect {

    private int mode;
    private Expression<?> first;
    private Expression<?> second;
    private Expression<?> third;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffCustomItem.class)
                .addPattern("save %itemstack% as [a] custom item %string%")
                .addPattern("force save %itemstack% as [a] custom item %string%")
                .addPattern("save custom item definition %string% from %string%")
                .addPattern("delete custom item %string%")
                .addPattern("reload [all] custom items")
                .addPattern("update custom items of %inventory%")
                .addPattern("consume %number% of custom item %string% from %inventory%")
                .build()
        );
    }

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        mode = matchedPattern;
        if (exprs.length > 0) first = exprs[0];
        if (exprs.length > 1) second = exprs[1];
        if (exprs.length > 2) third = exprs[2];
        return true;
    }

    @Override
    protected void execute(Event event) {
        CustomItemManager manager = CustomItemManager.get();
        if (manager == null) return;
        switch (mode) {
            case 0, 1 -> {
                ItemStack stack = (ItemStack) first.getSingle(event);
                String id = (String) second.getSingle(event);
                if (stack == null || stack.getType().isAir() || id == null) return;
                Map<String, Object> tree = ItemCodec.toTree(stack);
                ItemCodec.scrubInstanceTags(tree);
                String error = ItemCodec.check(tree);
                if (error != null) {
                    skwhy.SkWhy.getInstance().getLogger().warning("[CustomItem] save " + id + " : " + error);
                    return;
                }
                manager.registry().save(id, tree, mode == 1);
            }
            case 2 -> {
                String id = (String) first.getSingle(event);
                String snbt = (String) second.getSingle(event);
                Map<String, Object> tree = snbt == null ? null : QuestDefinitions.parseTree(snbt);
                if (id == null || tree == null) return;
                String error = ItemCodec.check(tree);
                if (error != null) {
                    skwhy.SkWhy.getInstance().getLogger().warning("[CustomItem] save " + id + " : " + error);
                    return;
                }
                manager.registry().save(id, tree, false);
            }
            case 3 -> {
                String id = (String) first.getSingle(event);
                if (id != null) manager.registry().remove(id);
            }
            case 4 -> manager.registry().reload();
            case 5 -> {
                Inventory inventory = (Inventory) first.getSingle(event);
                if (inventory == null) return;
                for (int slot = 0; slot < inventory.getSize(); slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    if (stack == null) continue;
                    ItemStack updated = CustomItemUpdater.update(stack, manager.registry());
                    if (updated != null) inventory.setItem(slot, updated);
                }
            }
            default -> {
                Number amount = (Number) first.getSingle(event);
                String id = (String) second.getSingle(event);
                Inventory inventory = (Inventory) third.getSingle(event);
                if (amount != null && id != null && inventory != null) CustomItemCounter.remove(inventory, id, amount.intValue());
            }
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return switch (mode) {
            case 0 -> "save " + first.toString(event, debug) + " as custom item " + second.toString(event, debug);
            case 1 -> "force save " + first.toString(event, debug) + " as custom item " + second.toString(event, debug);
            case 2 -> "save custom item definition " + first.toString(event, debug) + " from " + second.toString(event, debug);
            case 3 -> "delete custom item " + first.toString(event, debug);
            case 4 -> "reload custom items";
            case 5 -> "update custom items of " + first.toString(event, debug);
            default -> "consume " + first.toString(event, debug) + " of custom item " + second.toString(event, debug) + " from " + third.toString(event, debug);
        };
    }
}
