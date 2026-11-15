package skwhy.quest.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import skwhy.customitem.CustomItemDef;
import skwhy.customitem.CustomItemFactory;
import skwhy.customitem.CustomItemIdentity;
import skwhy.customitem.CustomItemManager;
import skwhy.customitem.ItemCodec;
import skwhy.customitem.LoreAttributes;

/**
 * Écrit un item (en main, dans un menu d'édition...) sous la forme d'une entrée de quête {@code {item: ..., count: n}} :
 * <ul>
 * <li>l'item est celui d'un item custom inchangé : {@code item: <id>} (+ {@code parameters} s'il en porte) ;</li>
 * <li>l'item est un matériau sans rien d'autre : {@code item: stone} ;</li>
 * <li>sinon (item vanilla personnalisé, item custom dont le contenu diffère de sa définition, comme un billet d'une autre valeur) :
 * {@code item: {material_id: ..., name: ...}}, au format d'une définition, qui devient un item custom anonyme.</li>
 * </ul>
 */
public final class QuestItemEntries {

    private QuestItemEntries() {}

    public static Map<String, Object> entryOf(ItemStack stack, int count) {
        Map<String, Object> entry = new LinkedHashMap<>();
        Map<String, Object> tree = ItemCodec.toTree(LoreAttributes.apply(stack, false));
        ItemCodec.scrubInstanceTags(tree);

        String id = CustomItemIdentity.idOf(stack);
        CustomItemManager manager = CustomItemManager.get();
        CustomItemDef def = id == null || manager == null ? null : manager.registry().get(id);
        if (def != null && sameAsDefinition(def, tree)) {
            entry.put("item", def.id());
            List<String> parameters = new ArrayList<>(CustomItemIdentity.parametersOf(stack));
            if (!parameters.isEmpty()) entry.put("parameters", parameters);
        } else if (tree.size() == 1) {
            entry.put("item", tree.get("material_id"));
        } else {
            entry.put("item", tree);
        }
        entry.put("count", Math.max(1, count));
        return entry;
    }

    private static boolean sameAsDefinition(CustomItemDef def, Map<String, Object> tree) {
        ItemStack fresh = CustomItemFactory.create(def, CustomItemFactory.Options.NONE);
        if (fresh == null) return false;
        Map<String, Object> expected = ItemCodec.toTree(fresh);
        ItemCodec.scrubInstanceTags(expected);
        return expected.equals(tree);
    }
}
