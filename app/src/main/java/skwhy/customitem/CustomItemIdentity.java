package skwhy.customitem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import net.minecraft.nbt.CompoundTag;

/**
 * Ce qu'un item en jeu garde de sa définition, dans {@code custom_data} : {@code item} (id normalisé), {@code version} et les
 * paramètres {@code owner} (uuid), {@code safe}, {@code save}, {@code invulnerable}, {@code preserve}. Le plugin ne fait que les
 * stocker : ce sont les scripts qui leur donnent un effet.
 */
public final class CustomItemIdentity {

    public static final List<String> FLAGS = List.of("safe", "save", "invulnerable", "preserve");

    private CustomItemIdentity() {}

    /** Id de l'item custom, ou {@code null}. Un ancien tag avec espaces est normalisé. */
    public static String idOf(ItemStack stack) {
        if (stack == null) return null;
        Object id = ItemNbt.data(stack).get("item");
        if (!(id instanceof String s)) return null;
        String normalized = CustomItemIds.normalize(s);
        return normalized.isEmpty() ? null : normalized;
    }

    public static boolean isCustom(ItemStack stack) {
        return idOf(stack) != null;
    }

    /** Version de l'item en jeu, {@code -1} s'il n'en a pas. */
    public static int versionOf(ItemStack stack) {
        return ItemNbt.data(stack).get("version") instanceof Number n ? n.intValue() : -1;
    }

    public static String ownerOf(ItemStack stack) {
        return ItemNbt.data(stack).get("owner") instanceof String s ? s : null;
    }

    /** Paramètres présents : les drapeaux à vrai, puis {@code owner} s'il y en a un. */
    public static List<String> parametersOf(ItemStack stack) {
        Map<String, Object> data = ItemNbt.data(stack);
        List<String> out = new ArrayList<>();
        for (String flag : FLAGS) {
            if (data.get(flag) instanceof Number n && n.intValue() != 0) out.add(flag);
        }
        if (data.get("owner") instanceof String) out.add("owner");
        return out;
    }

    /** Écrit {@code item} et {@code version}. */
    public static ItemStack mark(ItemStack stack, String id, int version) {
        return ItemNbt.edit(stack, tag -> {
            tag.putString("item", id);
            tag.putInt("version", version);
        });
    }

    /** Pose les paramètres ({@code owner} avec cet uuid, ou {@code {uuid}} s'il est {@code null}). */
    public static ItemStack applyParameters(ItemStack stack, List<String> parameters, String ownerUuid) {
        if (parameters.isEmpty()) return stack;
        return ItemNbt.edit(stack, tag -> applyTo(tag, parameters, ownerUuid));
    }

    private static void applyTo(CompoundTag tag, List<String> parameters, String ownerUuid) {
        for (String parameter : parameters) {
            if (parameter.equals("owner")) tag.putString("owner", ownerUuid == null ? "{uuid}" : ownerUuid);
            else if (FLAGS.contains(parameter)) tag.putBoolean(parameter, true);
        }
    }
}
