package skwhy.customitem;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Comptage et retrait d'items custom (par id) dans un inventaire. */
public final class CustomItemCounter {

    private CustomItemCounter() {}

    public static int count(Inventory inventory, String id) {
        String wanted = CustomItemIds.normalize(id);
        int total = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && wanted.equals(CustomItemIdentity.idOf(stack))) total += stack.getAmount();
        }
        return total;
    }

    /** Retire jusqu'à {@code amount} exemplaires ; retourne le nombre retiré. */
    public static int remove(Inventory inventory, String id, int amount) {
        String wanted = CustomItemIds.normalize(id);
        int left = amount;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || !wanted.equals(CustomItemIdentity.idOf(stack))) continue;
            int taken = Math.min(left, stack.getAmount());
            if (taken == stack.getAmount()) inventory.setItem(slot, null);
            else stack.setAmount(stack.getAmount() - taken);
            left -= taken;
        }
        return amount - left;
    }
}
