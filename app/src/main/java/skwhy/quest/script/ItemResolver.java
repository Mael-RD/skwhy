package skwhy.quest.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import skwhy.customitem.CustomItemCounter;
import skwhy.customitem.CustomItemDef;
import skwhy.customitem.CustomItemFactory;
import skwhy.customitem.CustomItemIdentity;
import skwhy.customitem.CustomItemManager;
import skwhy.quest.Tree;
import skwhy.util.TextComponents;

/**
 * Fabrique et compte les items cités par les quêtes. Une entrée ({@code items} / {@code result} / {@code buy1}...) est
 * {@code {item: ..., count: n}} où {@code item} est :
 * <ul>
 * <li>l'<b>id d'un item custom</b> (fichier {@code custom-items/<id>.yml}), avec en plus {@code placeholders} (ou {@code params}) : table
 * {@code clé: valeur} qui remplace les {@code {clé}} de la définition (ex. {@code value} d'un billet), et {@code parameters} : liste
 * {@code owner}, {@code safe}, {@code save}, {@code invulnerable}, {@code preserve} ;</li>
 * <li>un <b>matériau vanilla</b> ({@code stone}), sans id custom ;</li>
 * <li>une <b>table</b> au format d'une définition d'item custom ({@code material_id}, {@code name}, ...) pour un item utilisé à un seul
 * endroit : mêmes {@code placeholders} / {@code parameters} ;</li>
 * <li>l'ancienne table ({@code id}, {@code name}, {@code enchantments}, {@code pages}).</li>
 * </ul>
 * Un id est cherché parmi les items custom avant les matériaux.
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
        if (item instanceof Map<?, ?>) {
            Map<String, Object> table = Tree.map(item);
            stack = table.containsKey("material_id")
                ? CustomItemFactory.createAnonymous(table, options(entry, player))
                : fromTable(table);
        } else if (item != null) {
            stack = create(String.valueOf(item), entry, player);
        }
        if (stack == null) return null;
        stack.setAmount(Math.min(count, 99));
        return stack;
    }

    /** Un item par son identifiant : item custom, sinon matériau vanilla. */
    public static ItemStack create(String id, OfflinePlayer player) {
        return create(id, Map.of(), player);
    }

    private static ItemStack create(String id, Map<String, Object> entry, OfflinePlayer player) {
        CustomItemManager manager = CustomItemManager.get();
        CustomItemDef def = manager == null ? null : manager.registry().get(id);
        if (def != null) return CustomItemFactory.create(def, options(entry, player));
        Material material = material(id);
        return material == null ? null : new ItemStack(material);
    }

    private static CustomItemFactory.Options options(Map<String, Object> entry, OfflinePlayer player) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        for (String key : new String[] { "params", "placeholders" }) {
            for (Map.Entry<String, Object> e : Tree.map(entry, key).entrySet()) placeholders.put(e.getKey(), String.valueOf(e.getValue()));
        }
        List<String> parameters = new ArrayList<>();
        for (String parameter : Tree.strings(entry, "parameters")) {
            String lower = parameter.trim().toLowerCase(Locale.ROOT);
            if (lower.equals("owner") || CustomItemIdentity.FLAGS.contains(lower)) parameters.add(lower);
        }
        return new CustomItemFactory.Options(player, parameters, null, List.of(), placeholders);
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

    /** Nombre d'exemplaires de l'item dans l'inventaire du joueur (item custom par son id, sinon matériau vanilla). */
    public static int count(Player player, String id) {
        CustomItemManager manager = CustomItemManager.get();
        if (manager != null && manager.registry().has(id)) return CustomItemCounter.count(player.getInventory(), id);
        Material material = material(id);
        if (material == null) return 0;
        int total = 0;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.getType() == material) total += s.getAmount();
        }
        return total;
    }
}
