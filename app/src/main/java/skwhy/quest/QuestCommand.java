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
        super("skwhyquest", "Administration du module de quêtes SkWhy", "/skwhyquest <reload|save|convert|editor>", List.of());
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
            case "editor" -> editor(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** {@code editor link <quest|npc> <id> <joueur>}, {@code editor pending}, {@code editor apply|discard <id> [force]}. */
    private static void editor(CommandSender sender, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase() : "";
        switch (sub) {
            case "link" -> {
                boolean menu = args.length > 2 && args[2].equalsIgnoreCase("menu");
                if (args.length < (menu ? 4 : 5)) { sender.sendMessage("/skwhyquest editor link <quest|npc> <id> <joueur> [dialogue]  ou  link menu <joueur>"); return; }
                String who = menu ? args[3] : args[4];
                org.bukkit.OfflinePlayer player = Bukkit.getOfflinePlayer(who);
                String name = player.getName() == null ? who : player.getName();
                String url = skwhy.quest.editor.EditorService.createLink(menu ? "" : args[2].toLowerCase(), menu ? "" : args[3], player.getUniqueId(), name, !menu && args.length > 5 ? args[5] : null);
                sender.sendMessage(url == null ? "Éditeur indisponible ou introuvable : " + (menu ? "menu" : args[3] + (args.length > 5 ? " / dialogue " + args[5] : "")) : url);
            }
            case "pending" -> {
                List<skwhy.quest.editor.ChangeSet> list = skwhy.quest.editor.EditorService.pending();
                sender.sendMessage(list.size() + " modification(s) en attente.");
                for (skwhy.quest.editor.ChangeSet cs : list) {
                    sender.sendMessage(cs.id() + " joueur=" + cs.player() + " " + cs.total().total() + " valeur(s) : "
                        + cs.entries().stream().map(e -> e.kind() + ":" + e.id()).toList());
                }
            }
            case "apply", "discard" -> {
                if (args.length < 3) { sender.sendMessage("/skwhyquest editor " + sub + " <id> [force]"); return; }
                boolean force = args.length > 3 && args[3].equalsIgnoreCase("force");
                if (!skwhy.quest.editor.EditorService.answer(args[2], sub.equals("apply"), force, sender)) {
                    sender.sendMessage("Modification en attente introuvable : " + args[2]);
                }
            }
            default -> sender.sendMessage("/skwhyquest editor <link|pending|apply|discard>");
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("reload", "save", "convert", "editor");
        if (args.length == 2 && args[0].equalsIgnoreCase("editor")) return List.of("link", "pending", "apply", "discard");
        if (args.length == 3 && args[0].equalsIgnoreCase("editor") && args[1].equalsIgnoreCase("link")) return List.of("quest", "npc", "menu");
        return List.of();
    }
}
