package skwhy.quest;

import java.io.File;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import skwhy.quest.data.QuestStore;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestNpc;

/**
 * Commande d'administration {@code /skwhyquest} (permission {@code skwhy.quest.admin}, console incluse) :
 * {@code reload}, {@code save} et {@code convert} (fichiers NBT historiques vers YAML).
 */
public final class QuestCommand extends Command {

    private QuestCommand() {
        super("skwhyquest", "Administration du module de quêtes SkWhy", "/skwhyquest <reload|save|convert>", List.of());
        setPermission(QuestSettings.adminPermission());
    }

    public static void register() {
        Bukkit.getCommandMap().register("skwhy", new QuestCommand());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!testPermission(sender)) return true;
        if (args.length == 0) return false;
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                QuestRegistry.reloadAll();
                sender.sendMessage("Quêtes rechargées : " + QuestRegistry.quests().size() + " quête(s), " + QuestRegistry.npcs().size() + " NPC.");
            }
            case "save" -> {
                QuestStore.saveDirty();
                sender.sendMessage("Données de quêtes enregistrées.");
            }
            case "convert" -> {
                int quests = 0;
                int npcs = 0;
                for (Quest quest : QuestRegistry.quests()) {
                    if (YamlTree.write(new File(QuestSettings.questFolder(), quest.id() + ".yml"), quest.raw())) quests++;
                }
                for (QuestNpc npc : QuestRegistry.npcs()) {
                    if (YamlTree.write(new File(QuestSettings.npcFolder(), npc.id() + ".yml"), npc.raw())) npcs++;
                }
                sender.sendMessage(quests + " quête(s) et " + npcs + " NPC convertis en YAML dans " + QuestSettings.questFolder().getParent() + ".");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 ? List.of("reload", "save", "convert") : List.of();
    }
}
