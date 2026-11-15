package skwhy.customitem;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/**
 * Met un item en jeu à la version de sa définition : nom, lore, attributs, équipement, {@code effects} / {@code bijou} /
 * {@code armor_id} sont repris de la définition ; les enchantements ne baissent jamais (un niveau au-dessus du maximum est
 * ramené au maximum, ou au niveau de la définition s'il est plus haut). Le reste de l'item (dégâts, nom d'enclume, paramètres...) est gardé.
 */
public final class CustomItemUpdater {

    private static final String[] DATA_KEYS = { "effects", "bijou", "armor_id" };

    private CustomItemUpdater() {}

    /** @return l'item mis à jour, ou {@code null} s'il est déjà à jour (ou n'est pas un item custom connu) */
    @SuppressWarnings("unchecked")
    public static ItemStack update(ItemStack stack, CustomItemRegistry registry) {
        String id = CustomItemIdentity.idOf(stack);
        CustomItemDef def = id == null ? null : registry.get(id);
        if (def == null || CustomItemIdentity.versionOf(stack) == def.version()) return null;

        Map<String, Object> current = ItemCodec.toTree(stack);
        Map<String, Object> wanted = def.tree();
        for (String key : new String[] { "name", "lore", "attributes", "equippable" }) {
            if (wanted.containsKey(key)) current.put(key, wanted.get(key));
            else current.remove(key);
        }
        Map<String, Object> data = current.get("data") instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : new LinkedHashMap<>();
        Map<String, Object> wantedData = wanted.get("data") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        for (String key : DATA_KEYS) {
            data.remove(key);
            if (wantedData.containsKey(key)) data.put(key, wantedData.get(key));
        }
        data.put("item", id);
        data.put("version", def.version());
        current.put("data", data);
        current.put("enchantments", mergeEnchantments(current.get("enchantments"), wanted.get("enchantments")));
        if (((Map<?, ?>) current.get("enchantments")).isEmpty()) current.remove("enchantments");

        ItemStack updated = ItemCodec.fromTree(current);
        if (updated == null) return null;
        updated.setAmount(stack.getAmount());
        return LoreAttributes.apply(updated, true);
    }

    /** Niveau final : le plus haut de l'item et de la définition, sauf un niveau de l'item au-dessus du maximum (hors définition) ramené au maximum. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> mergeEnchantments(Object oldValue, Object newValue) {
        Map<String, Object> oldMap = oldValue instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> newMap = newValue instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> out = new LinkedHashMap<>(oldMap);
        for (Map.Entry<String, Object> e : newMap.entrySet()) {
            int wantedLevel = ((Number) e.getValue()).intValue();
            int oldLevel = oldMap.get(e.getKey()) instanceof Number n ? n.intValue() : 0;
            int max = maxLevel(e.getKey());
            if (wantedLevel > oldLevel) out.put(e.getKey(), wantedLevel);
            else if (oldLevel > max && oldLevel > wantedLevel) out.put(e.getKey(), Math.max(max, wantedLevel));
        }
        return out;
    }

    private static int maxLevel(String key) {
        NamespacedKey namespaced = NamespacedKey.fromString(key.contains(":") ? key : "minecraft:" + key);
        Enchantment enchantment = namespaced == null ? null : Registry.ENCHANTMENT.get(namespaced);
        return enchantment == null ? Integer.MAX_VALUE : enchantment.getMaxLevel();
    }
}
