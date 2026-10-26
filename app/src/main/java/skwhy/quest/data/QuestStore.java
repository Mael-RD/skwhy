package skwhy.quest.data;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import skwhy.SkWhy;
import skwhy.quest.QuestSettings;
import skwhy.quest.YamlTree;

/**
 * Cache mémoire des données de quête des joueurs. Les écritures disque sont groupées et faites sur un thread
 * dédié : les modifications de progression ne touchent jamais le disque sur le thread principal.
 */
public final class QuestStore {

    private static final Map<UUID, PlayerQuestData> CACHE = new ConcurrentHashMap<>();
    private static ExecutorService writer;
    private static BukkitTask autosave;

    private QuestStore() {}

    public static void start() {
        writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SkWhy-QuestStore");
            t.setDaemon(true);
            return t;
        });
        int interval = QuestSettings.saveIntervalTicks();
        autosave = Bukkit.getScheduler().runTaskTimer(SkWhy.getInstance(), QuestStore::saveDirty, interval, interval);
    }

    /** Sauvegarde tout puis arrête le thread d'écriture (à l'arrêt du plugin). */
    public static void shutdown() {
        if (autosave != null) autosave.cancel();
        List<Runnable> jobs = collectJobs(false);
        if (writer != null) {
            for (Runnable job : jobs) writer.submit(job);
            writer.shutdown();
            try { writer.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        } else {
            jobs.forEach(Runnable::run);
        }
        CACHE.clear();
    }

    /** Les données du joueur, chargées (ou importées de l'ancien format) si nécessaire. */
    public static PlayerQuestData get(UUID uuid) {
        PlayerQuestData data = CACHE.get(uuid);
        if (data != null) return data;
        data = load(uuid);
        PlayerQuestData previous = CACHE.putIfAbsent(uuid, data);
        return previous != null ? previous : data;
    }

    public static PlayerQuestData getIfLoaded(UUID uuid) {
        return CACHE.get(uuid);
    }

    private static PlayerQuestData load(UUID uuid) {
        File file = new File(QuestSettings.dataFolder(), uuid + ".yml");
        Map<String, Object> tree = YamlTree.read(file);
        PlayerQuestData data;
        if (tree != null) {
            data = PlayerQuestData.fromTree(uuid, tree);
        } else {
            data = QuestSettings.legacyImportEnabled() ? LegacyPlayerData.importQuests(uuid) : null;
            if (data == null) {
                data = new PlayerQuestData(uuid);
                data.compass = QuestSettings.compassAuto();
            }
        }
        skwhy.quest.QuestEngine.reindex(data);
        return data;
    }

    /** Décharge un joueur (déconnexion) après l'avoir sauvegardé si besoin. */
    public static void unload(UUID uuid) {
        PlayerQuestData data = CACHE.get(uuid);
        if (data == null) return;
        if (data.isDirty()) {
            Runnable job = job(data);
            if (writer != null && !writer.isShutdown()) writer.submit(job); else job.run();
        }
        CACHE.remove(uuid);
    }

    public static void saveDirty() {
        if (writer == null || writer.isShutdown()) return;
        for (Runnable job : collectJobs(true)) writer.submit(job);
        // Les joueurs hors-ligne consultés par un script ne restent pas en mémoire une fois sauvegardés
        for (PlayerQuestData data : CACHE.values()) {
            if (!data.isDirty() && Bukkit.getPlayer(data.uuid) == null) CACHE.remove(data.uuid);
        }
    }

    private static List<Runnable> collectJobs(boolean onlyDirty) {
        List<Runnable> jobs = new ArrayList<>();
        for (PlayerQuestData data : CACHE.values()) {
            if (onlyDirty && !data.isDirty()) continue;
            if (!data.isDirty() && !onlyDirty) continue;
            jobs.add(job(data));
        }
        return jobs;
    }

    private static Runnable job(PlayerQuestData data) {
        // L'instantané est construit tout de suite (thread principal) ; seule l'écriture est asynchrone
        Map<String, Object> snapshot = data.snapshot();
        data.dirty = false;
        File file = new File(QuestSettings.dataFolder(), data.uuid + ".yml");
        return () -> {
            if (!YamlTree.write(file, snapshot)) {
                SkWhy.getInstance().getLogger().warning("[Quest] Impossible d'écrire " + file.getPath());
                data.dirty = true;
            }
        };
    }
}
