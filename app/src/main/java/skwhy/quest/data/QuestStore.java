package skwhy.quest.data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import net.minecraft.nbt.CompoundTag;
import skwhy.SkWhy;
import skwhy.quest.QuestSettings;

/**
 * Données de quête des joueurs, stockées dans leur NBT custom ({@code quest;liste;<quête>;...}, voir {@link PlayerNbt}) :
 * les scripts continuent de les lire et de les écrire avec {@code player_nbt}. Le module garde un objet par joueur ;
 * toute modification est écrite tout de suite dans le NBT (celui du joueur connecté, ou le fichier d'un joueur
 * déconnecté), et une modification faite par un script est relue au prochain accès. Un joueur sans données du tout
 * (UUID inconnu du serveur) reste seulement en mémoire.
 */
public final class QuestStore {

    private static final Map<UUID, PlayerQuestData> CACHE = new ConcurrentHashMap<>();
    private static BukkitTask sweep;

    private QuestStore() {}

    public static void start() {
        PlayerQuestData.onDirty = QuestStore::onDirty;
        int interval = QuestSettings.saveIntervalTicks();
        sweep = Bukkit.getScheduler().runTaskTimer(SkWhy.getInstance(), QuestStore::saveDirty, interval, interval);
    }

    /** Écrit tout ce qui reste à écrire (à l'arrêt du plugin). */
    public static void shutdown() {
        if (sweep != null) sweep.cancel();
        for (PlayerQuestData data : CACHE.values()) if (data.isDirty()) flush(data);
        CACHE.clear();
    }

    /** Les données du joueur, relues du NBT si un script les a modifiées depuis. */
    public static PlayerQuestData get(UUID uuid) {
        PlayerQuestData data = CACHE.get(uuid);
        if (data != null) {
            if (!data.dirty) {
                long now = PlayerNbt.stamp(uuid);
                if (now != Long.MIN_VALUE && now != data.stamp) {
                    data.replaceWith(read(uuid));
                    data.stamp = now;
                    skwhy.quest.QuestEngine.reindex(data);
                }
            }
            return data;
        }
        data = read(uuid);
        data.stamp = PlayerNbt.stamp(uuid);
        skwhy.quest.QuestEngine.reindex(data);
        PlayerQuestData previous = CACHE.putIfAbsent(uuid, data);
        return previous != null ? previous : data;
    }

    public static PlayerQuestData getIfLoaded(UUID uuid) {
        return CACHE.get(uuid);
    }

    private static PlayerQuestData read(UUID uuid) {
        PlayerQuestData data = LegacyPlayerData.importQuests(uuid);
        if (data != null) return data;
        data = new PlayerQuestData(uuid);
        data.compass = QuestSettings.compassAuto();
        return data;
    }

    /** Appelé par {@link PlayerQuestData#markDirty()} : seules les données du cache sont écrites (tout de suite : un script relit le NBT juste après). */
    static void onDirty(PlayerQuestData data) {
        if (CACHE.get(data.uuid) == data) flush(data);
    }

    /** Écrit les données dans le NBT du joueur (ou les garde en mémoire si le serveur ne le connaît pas). */
    private static void flush(PlayerQuestData data) {
        PlayerNbt.Access access = PlayerNbt.open(data.uuid, PlayerNbt.exists(data.uuid));
        if (access == null) {
            data.dirty = false;
            return;
        }
        CompoundTag quest = access.custom.getCompound("quest").orElse(null);
        if (quest == null) {
            quest = new CompoundTag();
            access.custom.put("quest", quest);
        }
        PlayerQuestNbt.write(data, quest);
        if (!access.commit()) return;
        data.dirty = false;
        data.stamp = PlayerNbt.stamp(data.uuid);
    }

    /** Décharge un joueur (déconnexion). */
    public static void unload(UUID uuid) {
        PlayerQuestData data = CACHE.get(uuid);
        if (data == null) return;
        if (data.isDirty()) flush(data);
        CACHE.remove(uuid);
    }

    public static void saveDirty() {
        for (PlayerQuestData data : CACHE.values()) if (data.isDirty()) flush(data);
        // Les joueurs hors-ligne consultés par un script ne restent pas en mémoire une fois écrits
        for (PlayerQuestData data : CACHE.values()) {
            if (!data.isDirty() && Bukkit.getPlayer(data.uuid) == null && PlayerNbt.exists(data.uuid)) CACHE.remove(data.uuid);
        }
    }
}
