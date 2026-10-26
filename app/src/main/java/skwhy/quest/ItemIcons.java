package skwhy.quest;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import skwhy.quest.model.Quest;
import skwhy.quest.script.ItemResolver;

/** Icône d'une quête : un item vanilla, ou une tête décorée par l'URL d'une texture ({@code head}). */
public final class ItemIcons {

    private ItemIcons() {}

    public static ItemStack of(Quest quest) {
        Map<String, Object> icon = quest.icon();
        if (icon.isEmpty()) return null;
        Material material = ItemResolver.material(Tree.str(icon, "id", "stone"));
        if (material == null) return null;
        ItemStack stack = new ItemStack(material);
        String head = Tree.str(icon, "head");
        if (head != null && stack.getItemMeta() instanceof SkullMeta meta) {
            try {
                PlayerProfile profile = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(head.getBytes()), "quest");
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(URI.create(head).toURL());
                profile.setTextures(textures);
                meta.setOwnerProfile(profile);
                stack.setItemMeta(meta);
            } catch (MalformedURLException | IllegalArgumentException ignored) {
                // URL invalide : la tête reste vide
            }
        }
        return stack;
    }
}
