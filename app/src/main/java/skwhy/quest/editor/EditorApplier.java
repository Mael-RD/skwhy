package skwhy.quest.editor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import skwhy.SkWhy;
import skwhy.quest.QuestEngine;
import skwhy.quest.QuestRegistry;
import skwhy.quest.QuestSettings;
import skwhy.quest.YamlTree;
import skwhy.quest.data.QuestStore;
import skwhy.quest.npc.IndicatorService;

/** Écrit sur le disque les modifications validées par le joueur, puis recharge les définitions. À appeler sur le thread principal. */
final class EditorApplier {

    /** Résultat : ce qui a été écrit, ce qui est en conflit de version, ce qui n'a pas pu être écrit. */
    record Result(List<ChangeSet.Entry> applied, List<ChangeSet.Entry> conflicts, List<ChangeSet.Entry> failed) {}

    private EditorApplier() {}

    /**
     * @param force {@code true} : écrase même si le fichier a changé depuis l'ouverture de l'éditeur
     *              (sinon, si un seul fichier a changé, rien n'est écrit)
     */
    static Result apply(ChangeSet changes, EditorData data, boolean force) {
        List<ChangeSet.Entry> conflicts = new ArrayList<>();
        if (!force) {
            for (ChangeSet.Entry e : changes.entries()) {
                String current = data.version(e.kind(), e.id());
                if (current == null || !current.equals(e.base())) conflicts.add(e);
            }
            if (!conflicts.isEmpty()) return new Result(List.of(), conflicts, List.of());
        }
        List<ChangeSet.Entry> applied = new ArrayList<>();
        List<ChangeSet.Entry> failed = new ArrayList<>();
        for (ChangeSet.Entry e : changes.entries()) {
            if (write(e)) applied.add(e);
            else failed.add(e);
        }
        if (!applied.isEmpty()) refreshPlayers();
        return new Result(applied, List.of(), failed);
    }

    private static boolean write(ChangeSet.Entry e) {
        Logger log = SkWhy.getInstance().getLogger();
        if (e.id().isBlank() || e.id().contains("/") || e.id().contains("\\") || e.id().contains("..")) return false;
        File folder = e.isNpc() ? QuestSettings.npcFolder() : QuestSettings.questFolder();
        File file = new File(folder, e.id() + ".yml");
        backup(file, e);
        if (!YamlTree.write(file, e.tree())) {
            log.warning("[QuestEditor] Écriture impossible : " + file.getPath());
            return false;
        }
        if (e.isNpc()) QuestRegistry.reloadNpc(e.id());
        else QuestRegistry.reloadQuest(e.id());
        log.info("[QuestEditor] " + (e.isNpc() ? "NPC " : "Quête ") + e.id() + " enregistré (+" + e.stat().added()
            + " -" + e.stat().removed() + " ~" + e.stat().modified() + ").");
        return true;
    }

    /** Garde une copie du fichier remplacé (les dernières {@code backups_per_entity}). */
    private static void backup(File file, ChangeSet.Entry e) {
        int keep = EditorSettings.backupsPerEntity();
        if (keep <= 0 || !file.isFile()) return;
        File folder = new File(EditorSettings.backupFolder(), e.kind());
        try {
            Files.createDirectories(folder.toPath());
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
            Files.copy(file.toPath(), new File(folder, e.id() + "." + stamp + ".yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
            File[] old = folder.listFiles((dir, name) -> name.startsWith(e.id() + ".") && name.endsWith(".yml")
                && name.length() == e.id().length() + ".".length() + stamp.length() + ".yml".length());
            if (old != null && old.length > keep) {
                Arrays.sort(old, Comparator.comparing(File::getName));
                for (int i = 0; i < old.length - keep; i++) old[i].delete();
            }
        } catch (IOException ex) {
            SkWhy.getInstance().getLogger().warning("[QuestEditor] Sauvegarde impossible de " + file.getName() + " : " + ex.getMessage());
        }
    }

    /** Les joueurs connectés reprennent la nouvelle définition (étapes, marqueurs de NPC). */
    private static void refreshPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            skwhy.quest.data.PlayerQuestData data = QuestStore.getIfLoaded(player.getUniqueId());
            if (data != null) QuestEngine.reindex(data);
            IndicatorService.invalidate(player.getUniqueId());
        }
    }
}
