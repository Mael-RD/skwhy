package skwhy.quest;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import skwhy.SkWhy;
import skwhy.quest.data.PlayerNbt;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestNpc;

/**
 * Lecture et écriture des définitions de quêtes et de NPC sous forme de NBT texte (SNBT) : les éditeurs en jeu
 * (scripts) manipulent des compounds NBT, le module garde ses définitions en YAML.
 */
public final class QuestDefinitions {

    private QuestDefinitions() {}

    public static String snbt(Quest quest) {
        return Snbt.write(quest.raw());
    }

    public static String snbt(QuestNpc npc) {
        return Snbt.write(npc.raw());
    }

    /** Enregistre la quête {@code id} en YAML (qui prime sur un éventuel ancien fichier NBT) puis la recharge. */
    public static boolean saveQuest(String id, String snbt) {
        Map<String, Object> tree = parseTree(snbt);
        if (tree == null || !YamlTree.write(new File(QuestSettings.questFolder(), id + ".yml"), tree)) return false;
        QuestRegistry.reloadQuest(id);
        return true;
    }

    public static boolean saveNpc(String id, String snbt) {
        Map<String, Object> tree = parseTree(snbt);
        if (tree == null || !YamlTree.write(new File(QuestSettings.npcFolder(), id + ".yml"), tree)) return false;
        QuestRegistry.reloadNpc(id);
        return true;
    }

    /** @return l'arbre, ou {@code null} si le texte n'est pas un compound NBT valide */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseTree(String snbt) {
        try {
            CompoundTag tag = TagParser.parseCompoundFully(snbt);
            Object tree = booleans(PlayerNbt.toTree(tag));
            return (Map<String, Object>) tree;
        } catch (CommandSyntaxException e) {
            SkWhy.getInstance().getLogger().warning("[Quest] NBT texte invalide : " + e.getMessage());
            return null;
        }
    }

    /** Un octet est un booléen dans les définitions ({@code boolean tag}). */
    private static Object booleans(Object value) {
        if (value instanceof Byte b) return b == 0 || b == 1 ? (Object) (b == 1) : (Object) (int) b;
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) out.put(String.valueOf(e.getKey()), booleans(e.getValue()));
            return out;
        }
        if (value instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object o : l) out.add(booleans(o));
            return out;
        }
        return value;
    }
}
