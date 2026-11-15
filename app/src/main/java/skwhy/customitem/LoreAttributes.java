package skwhy.customitem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Lignes « Indestructible » / « À joueur » ajoutées en bas du lore des items qui ont le paramètre {@code invulnerable} ou
 * {@code owner}. Elles sont reconnues par leurs deux couleurs (rouge {@code #ff5555} puis {@code #a5ab70}), y compris celles
 * écrites par l'ancien système Skript.
 */
public final class LoreAttributes {

    private static final TextColor LABEL = TextColor.color(0xff5555);
    private static final TextColor END = TextColor.color(0xa5ab70);
    private static final Pattern MARKED = Pattern.compile("§x§f§f§5§5§5§5.+§x§a§5§a§b§7§0", Pattern.CASE_INSENSITIVE);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private LoreAttributes() {}

    static boolean isMarked(Component line) {
        return MARKED.matcher(LEGACY.serialize(line)).find();
    }

    private static Component marked(String text) {
        return Component.text(text, LABEL).append(Component.text(" ", END));
    }

    /** Retire les lignes d'attributs, puis (si {@code show}) les remet d'après les paramètres de l'item. */
    public static ItemStack apply(ItemStack stack, boolean show) {
        ItemLore current = stack.getData(DataComponentTypes.LORE);
        List<Component> lines = new ArrayList<>();
        if (current != null) {
            for (Component line : current.lines()) if (!isMarked(line)) lines.add(line);
        }
        if (show) {
            List<String> parameters = CustomItemIdentity.parametersOf(stack);
            boolean invulnerable = parameters.contains("invulnerable");
            String owner = CustomItemIdentity.ownerOf(stack);
            if (invulnerable || owner != null) {
                lines.add(marked(" "));
                if (invulnerable) lines.add(marked("Indestructible"));
                if (owner != null) lines.add(marked("À " + ownerName(owner)));
            }
        }
        ItemStack copy = stack.clone();
        if (lines.isEmpty()) copy.resetData(DataComponentTypes.LORE);
        else copy.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
        return copy;
    }

    private static String ownerName(String owner) {
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(owner));
            if (player.getName() != null) return player.getName();
        } catch (IllegalArgumentException ignored) {
            // {uuid} non remplacé
        }
        return "{name}";
    }
}
