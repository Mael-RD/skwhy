package skwhy.customitem;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import skwhy.quest.data.PlayerNbt;

/** Accès au {@code custom_data} d'un item (NMS). Les modifications retournent un nouvel item, l'original n'est pas touché. */
public final class ItemNbt {

    private ItemNbt() {}

    /** Copie du {@code custom_data}, vide s'il n'y en a pas. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> data(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return new LinkedHashMap<>();
        CustomData data = CraftItemStack.asNMSCopy(stack).get(DataComponents.CUSTOM_DATA);
        if (data == null) return new LinkedHashMap<>();
        Object tree = PlayerNbt.toTree(data.copyTag());
        return tree instanceof Map<?, ?> ? (Map<String, Object>) tree : new LinkedHashMap<>();
    }

    /** Copie de l'item dont le {@code custom_data} est modifié par {@code editor}. */
    public static ItemStack edit(ItemStack stack, Consumer<CompoundTag> editor) {
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(stack);
        CustomData.update(DataComponents.CUSTOM_DATA, nms, editor);
        return CraftItemStack.asBukkitCopy(nms);
    }
}
