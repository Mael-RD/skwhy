package skwhy.quest.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import skwhy.quest.QuestSettings;
import skwhy.quest.Tree;
import skwhy.util.TextComponents;

/**
 * Fabrique et compte les items cités par les quêtes. Un item peut être décrit par une table
 * ({@code id}, {@code name}, {@code enchantments}, {@code pages}) ou par un nom d'item personnalisé du serveur, résolu
 * par la fonction Skript {@code quests.item_function}.
 */
public final class ItemResolver {

    private ItemResolver() {}

    /**
     * @param entry une entrée de {@code items} / {@code result} / {@code buy1}... : {@code {item: <id ou table>, count: n}}
     */
    public static ItemStack read(Map<String, Object> entry, OfflinePlayer player) {
        if (entry == null || entry.isEmpty()) return null;
        int count = Math.max(1, Tree.integer(entry, "count", 1));
        Object item = entry.get("item");
        ItemStack stack = null;
        if (item instanceof Map<?, ?>) stack = fromTable(Tree.map(item));
        else if (item != null) stack = create(String.valueOf(item), player);
        if (stack == null) return null;
        stack.setAmount(Math.min(count, 99));
        return stack;
    }

    /** Un item par son identifiant : matériau vanilla, sinon item personnalisé du serveur. */
    public static ItemStack create(String id, OfflinePlayer player) {
        Material material = material(id);
        if (material != null) return new ItemStack(material);
        Object custom = SkriptFunctions.call(QuestSettings.itemFunction(), id, player.getPlayer());
        return toStack(custom);
    }

    private static ItemStack toStack(Object value) {
        if (value instanceof ItemStack s) return s.clone();
        if (value instanceof ch.njol.skript.aliases.ItemType t) return t.getRandom();
        return null;
    }

    public static Material material(String id) {
        if (id == null || id.isEmpty()) return null;
        String key = id.toLowerCase(Locale.ROOT).replace(' ', '_');
        Material m = Material.matchMaterial(key);
        return m != null && m.isItem() ? m : null;
    }

    private static ItemStack fromTable(Map<String, Object> table) {
        Material material = material(Tree.str(table, "id", "stone"));
        if (material == null) return null;
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        String name = Tree.str(table, "name");
        if (name != null) meta.displayName(TextComponents.fromSkript(name.replace('§', '&')).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : Tree.strings(table, "lore")) lore.add(TextComponents.fromSkript(line).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) meta.lore(lore);
        for (Map.Entry<String, Object> e : Tree.map(table, "enchantments").entrySet()) {
            Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.fromString(e.getKey().contains(":") ? e.getKey() : "minecraft:" + e.getKey()));
            if (enchantment != null && e.getValue() instanceof Number level) meta.addEnchant(enchantment, level.intValue(), true);
        }
        if (meta instanceof BookMeta book) {
            List<String> pages = Tree.strings(table, "pages");
            if (!pages.isEmpty()) book.setPages(pages);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** Nombre d'exemplaires de l'item dans l'inventaire du joueur. */
    public static int count(Player player, String id) {
        Material material = material(id);
        if (material != null) {
            int total = 0;
            for (ItemStack s : player.getInventory().getContents()) {
                if (s != null && s.getType() == material) total += s.getAmount();
            }
            return total;
        }
        Object value = SkriptFunctions.call(QuestSettings.itemCountFunction(), id.replace('_', ' '), player);
        return value instanceof Number n ? n.intValue() : 0;
    }
}
