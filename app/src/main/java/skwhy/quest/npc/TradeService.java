package skwhy.quest.npc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.view.MerchantView;

import io.papermc.paper.event.player.PlayerPurchaseEvent;
import net.kyori.adventure.text.Component;
import skwhy.quest.Tree;
import skwhy.quest.script.Conditions;
import skwhy.quest.script.ItemResolver;
import skwhy.quest.script.ScriptContext;
import skwhy.quest.script.ScriptEngine;
import skwhy.util.TextComponents;

/**
 * Menus d'échange des NPC. Chaque échange ({@code result}, {@code buy1}, {@code buy2}) peut avoir une
 * {@code condition} (l'échange n'est proposé que si elle est vraie), un nombre d'utilisations {@code uses} et une
 * {@code action} exécutée à chaque achat.
 */
public final class TradeService {

    private record Session(Merchant merchant, ScriptContext ctx, Map<Integer, String> actions) {}

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private TradeService() {}

    public static void open(Player player, Map<String, Object> trade, ScriptContext ctx) {
        if (trade == null || trade.isEmpty()) return;
        String name = Tree.str(trade, "name");
        Component title = name == null ? Component.text("Échanges") : TextComponents.fromSkript(name);
        Merchant merchant = Bukkit.createMerchant();
        List<MerchantRecipe> recipes = new ArrayList<>();
        Map<Integer, String> actions = new HashMap<>();
        int index = 0;
        for (Map<String, Object> entry : Tree.maps(trade, "trades")) {
            index++;
            String condition = Tree.str(entry, "condition");
            if (condition != null && !Conditions.eval(ctx, condition)) continue;
            ItemStack result = ItemResolver.read(Tree.map(entry, "result"), player);
            if (result == null) continue;
            MerchantRecipe recipe = new MerchantRecipe(result, 0, entry.containsKey("uses") ? Tree.integer(entry, "uses", Integer.MAX_VALUE) : Integer.MAX_VALUE, false, index, 0f);
            List<ItemStack> ingredients = new ArrayList<>();
            for (String key : new String[]{"buy1", "buy2"}) {
                ItemStack buy = ItemResolver.read(Tree.map(entry, key), player);
                if (buy != null) ingredients.add(buy);
            }
            recipe.setIngredients(ingredients);
            recipes.add(recipe);
            if (entry.containsKey("action")) actions.put(index, Tree.str(entry, "action"));
        }
        merchant.setRecipes(recipes);
        SESSIONS.put(player.getUniqueId(), new Session(merchant, ctx, actions));
        MerchantView view = MenuType.MERCHANT.builder().merchant(merchant).title(title).checkReachable(false).build(player);
        if (view != null) player.openInventory(view);
    }

    public static void onPurchase(PlayerPurchaseEvent event) {
        Session session = SESSIONS.get(event.getPlayer().getUniqueId());
        if (session == null || !session.merchant.equals(event.getMerchant())) return;
        String action = session.actions.get(event.getTrade().getVillagerExperience());
        if (action != null) ScriptEngine.run(session.ctx, action, null);
    }

    public static void onClose(Player player) {
        SESSIONS.remove(player.getUniqueId());
    }
}
