package skwhy;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import skwhy.modules.APIModule;
import skwhy.quest.editor.EditorService;

import java.util.List;

/**
 * Commande {@code /skwhy reload} (permission {@code skwhy.reload}, console incluse) : relit {@code config.yml} puis redémarre le serveur
 * HTTP (API REST + éditeur de quêtes), seuls à ne lire port / HTTPS / certificat / Let's Encrypt qu'à leur démarrage.
 * Les autres réglages sont lus à chaque usage. Activer / désactiver un module (clés {@code modules.*}) demande toujours un redémarrage.
 */
public final class SkWhyCommand extends Command {

    public SkWhyCommand() {
        super("skwhy", "Administration de SkWhy", "/skwhy reload", List.of());
        setPermission("skwhy.reload");
    }

    public static void register() {
        Bukkit.getCommandMap().register("skwhy", new SkWhyCommand());
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!testPermission(sender)) return true;
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("/skwhy reload : recharge config.yml et redémarre le serveur HTTP (API + éditeur)");
            return true;
        }
        SkWhy plugin = SkWhy.getInstance();
        plugin.reloadSettings();
        sender.sendMessage("config.yml rechargée.");

        // Les deux utilisateurs du serveur HTTP sont arrêtés avant de repartir : il ne se ferme (et ne relit sa config) que quand plus personne ne l'utilise
        boolean editor = plugin.isModuleEnabled("modules.quest");
        EditorService.stop();
        APIModule.stopHttp();
        APIModule.startHttp();
        if (editor) EditorService.start();

        sender.sendMessage("Serveur HTTP : API " + (APIModule.isHttpRunning() ? "démarrée" : "arrêtée")
                + ", éditeur " + (EditorService.isStarted() ? "démarré" : "arrêté") + " (détails dans la console).");
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        return args.length == 1 && "reload".startsWith(args[0].toLowerCase()) ? List.of("reload") : List.of();
    }
}
