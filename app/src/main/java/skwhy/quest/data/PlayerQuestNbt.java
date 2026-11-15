package skwhy.quest.data;

import java.util.ArrayList;
import java.util.Map;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Écriture des données de quête d'un joueur dans son NBT (format historique, lu et écrit aussi par les scripts). */
final class PlayerQuestNbt {

    private PlayerQuestNbt() {}

    private static ListTag strings(java.util.Collection<String> values) {
        ListTag list = new ListTag();
        for (String v : values) list.add(StringTag.valueOf(v));
        return list;
    }

    private static CompoundTag child(CompoundTag parent, String key) {
        CompoundTag c = parent.getCompound(key).orElse(null);
        if (c == null) {
            c = new CompoundTag();
            parent.put(key, c);
        }
        return c;
    }

    private static void putList(CompoundTag q, String key, java.util.Collection<String> values) {
        if (!values.isEmpty() || q.contains(key)) q.put(key, strings(values));
    }

    /**
     * Écrit les données dans le compound {@code quest} du NBT du joueur. Ce que le module ne gère pas (index de l'ancien
     * moteur, variables listes ou compounds, {@code sources}...) est laissé tel quel.
     */
        /**
     * Écrit les données dans le compound {@code quest} du NBT du joueur. Ce que le module ne gère pas (index de l'ancien
     * moteur, variables listes ou compounds, {@code sources}...) est laissé tel quel.
     */
    static void write(PlayerQuestData d, CompoundTag q) {
        if (d.selected != null) q.putString("sélectionnée", d.selected); else q.remove("sélectionnée");
        putList(q, "catégorie", d.categories);
        putList(q, "npcs", d.metNpcs);
        putList(q, "npc_name", d.knownNames);
        if (!d.npcPositions.isEmpty() || q.contains("npcs_pos")) {
            CompoundTag pos = child(q, "npcs_pos");
            for (String k : new ArrayList<>(pos.keySet())) if (!d.npcPositions.containsKey(k)) pos.remove(k);
            for (Map.Entry<String, Integer> e : d.npcPositions.entrySet()) pos.put(e.getKey(), IntTag.valueOf(e.getValue()));
        }
        if (!d.dialogues.isEmpty() || q.contains("dialogues")) {
            CompoundTag dia = child(q, "dialogues");
            for (String k : new ArrayList<>(dia.keySet())) if (!d.dialogues.containsKey(k)) dia.remove(k);
            for (Map.Entry<String, String> e : d.dialogues.entrySet()) dia.put(e.getKey(), StringTag.valueOf(e.getValue()));
        }
        q.put("boussole", ByteTag.valueOf(d.compass));
        if (!"all".equals(d.filterState) || !"all".equals(d.filterType) || q.contains("filtre")) {
            CompoundTag f = child(q, "filtre");
            f.putString("etat", d.filterState);
            f.putString("type", d.filterType);
        }
        CompoundTag liste = child(q, "liste");
        for (String k : new ArrayList<>(liste.keySet())) {
            CompoundTag old = liste.getCompound(k).orElse(null);
            if (d.quests.containsKey(k) || old == null || !old.contains("statut")) continue;
            // Quête remise à zéro : comme avant, seuls le statut, l'étape et la progression disparaissent (variables et récompense restent)
            old.remove("statut");
            old.remove("step");
            old.remove("progress");
            if (old.isEmpty()) liste.remove(k);
        }
        for (Map.Entry<String, QuestProgress> e : d.quests.entrySet()) {
            QuestProgress p = e.getValue();
            CompoundTag c = child(liste, e.getKey());
            c.putString("statut", p.status.legacyName() != null ? p.status.legacyName() : p.status.id());
            c.put("step", IntTag.valueOf(p.step));
            if (!p.progress.isEmpty() || c.contains("progress")) {
                CompoundTag prog = child(c, "progress");
                for (String k : new ArrayList<>(prog.keySet())) {
                    try { if (!p.progress.containsKey(Integer.parseInt(k))) prog.remove(k); } catch (NumberFormatException ignored) { }
                }
                for (Map.Entry<Integer, Object> pe : p.progress.entrySet()) {
                    prog.put(String.valueOf(pe.getKey()), pe.getValue() instanceof Boolean b ? ByteTag.valueOf(b)
                        : IntTag.valueOf(pe.getValue() instanceof Number n ? n.intValue() : 0));
                }
            }
            if (!p.variables.isEmpty() || c.contains("variables")) {
                CompoundTag vars = child(c, "variables");
                for (String k : p.removedVariables) if (!p.variables.containsKey(k)) vars.remove(k);
                p.removedVariables.clear();
                for (Map.Entry<String, Object> ve : p.variables.entrySet()) {
                    Tag existing = vars.get(ve.getKey());
                    if (existing != null && sameValue(existing, ve.getValue())) continue;
                    vars.put(ve.getKey(), PlayerNbt.scalarTag(ve.getValue()));
                }
            }
            if (p.rewardClaimed) c.put("récompense", ByteTag.valueOf(true)); else c.remove("récompense");
        }
    }

    /** {@code true} si le tag existant donne déjà cette valeur une fois lu (conserve le type : octet, décimal...). */
    private static boolean sameValue(Tag existing, Object value) {
        Object read = PlayerNbt.toTree(existing);
        if (read instanceof Byte b) read = (int) b;
        if (read instanceof Number r && value instanceof Number v) return r.doubleValue() == v.doubleValue();
        if (read instanceof Number r && value instanceof Boolean v) return r.doubleValue() == (v ? 1 : 0);
        return read != null && read.equals(value);
    }
}
