package skwhy.customitem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.nbt.CompoundTag;

/** Fabrique les items d'après leur définition. */
public final class CustomItemFactory {

    /**
     * @param player propriétaire par défaut et source de {@code {uuid}} / {@code {name}} (peut être {@code null})
     * @param parameters {@code owner}, {@code safe}, {@code save}, {@code invulnerable}, {@code preserve}
     * @param name remplace le nom ({@code [name]} = le nom de la définition), ou {@code null}
     * @param lore lignes ajoutées au lore ({@code [lore]} = le lore de la définition), ou vide
     * @param placeholders {@code clé → valeur}, pour les {@code {clé}} de la définition
     */
    public record Options(OfflinePlayer player, List<String> parameters, String name, List<String> lore, Map<String, String> placeholders) {
        public static final Options NONE = new Options(null, List.of(), null, List.of(), Map.of());
    }

    private CustomItemFactory() {}

    /** @return l'item (1 exemplaire), ou {@code null} si la définition n'est pas valide pour le serveur */
    public static ItemStack create(CustomItemDef def, Options options) {
        ItemStack stack = build(def.tree(), options);
        return stack == null ? null : CustomItemIdentity.mark(stack, def.id(), def.version());
    }

    /**
     * Item d'après un arbre de définition qui n'a pas d'id (item utilisé à un seul endroit, par exemple dans une quête) : mêmes
     * placeholders et paramètres, mais l'item ne porte ni id ni version.
     *
     * @return l'item, ou {@code null} si l'arbre n'est pas valide pour le serveur
     */
    public static ItemStack createAnonymous(Map<String, Object> tree, Options options) {
        return build(CustomItemDef.deepCopy(tree), options);
    }

    private static ItemStack build(Map<String, Object> source, Options options) {
        Map<String, String> values = new LinkedHashMap<>(options.placeholders());
        if (options.player() != null) {
            values.putIfAbsent("uuid", options.player().getUniqueId().toString());
            if (options.player().getName() != null) values.putIfAbsent("name", options.player().getName());
        }
        Map<String, Object> tree = Placeholders.apply(source, values);
        if (options.name() != null) {
            String base = tree.get("name") instanceof String s ? s : "";
            tree.put("name", options.name().replace("[name]", base));
        }
        if (!options.lore().isEmpty()) {
            List<String> own = new ArrayList<>();
            if (tree.get("lore") instanceof List<?> lines) for (Object line : lines) own.add(String.valueOf(line));
            tree.put("lore", new ArrayList<Object>(Placeholders.mergeLore(own, options.lore())));
        }
        ItemStack stack = ItemCodec.fromTree(tree);
        if (stack == null) return null;
        String owner = options.player() == null ? null : options.player().getUniqueId().toString();
        stack = CustomItemIdentity.applyParameters(stack, options.parameters(), owner);
        return LoreAttributes.apply(stack, true);
    }

    /** L'item affiché quand un id n'existe pas : une barrière qui garde l'id attendu ({@code attendu}). */
    public static ItemStack unknown(String id) {
        ItemStack stack = new ItemStack(Material.BARRIER);
        stack.setData(DataComponentTypes.CUSTOM_NAME, Component.text("Item inconnu", NamedTextColor.RED));
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
            Component.text("Item attendu :", NamedTextColor.GRAY), Component.text(id, NamedTextColor.AQUA))));
        return ItemNbt.edit(stack, (CompoundTag tag) -> {
            tag.putBoolean("safe", true);
            tag.putString("attendu", id);
        });
    }
}
