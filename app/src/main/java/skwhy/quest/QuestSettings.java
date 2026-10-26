package skwhy.quest;

import java.io.File;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;

import skwhy.SkWhy;

/**
 * Réglages du module de quêtes, lus dans la section {@code quests.*} de la config de SkWhy. Chaque clé a une valeur
 * par défaut : le module fonctionne sans aucune entrée dans {@code config.yml}.
 */
public final class QuestSettings {

    private QuestSettings() {}

    private static FileConfiguration config() {
        return SkWhy.getInstance().getConfig();
    }

    /** Dossier des quêtes au format YAML. */
    public static File questFolder() {
        return new File(SkWhy.getInstance().getDataFolder(), config().getString("quests.folder", "quests"));
    }

    /** Dossier des NPC au format YAML. */
    public static File npcFolder() {
        return new File(SkWhy.getInstance().getDataFolder(), config().getString("quests.npc_folder", "quest-npcs"));
    }

    /** Dossier des données de progression des joueurs. */
    public static File dataFolder() {
        return new File(SkWhy.getInstance().getDataFolder(), config().getString("quests.data_folder", "quest-data"));
    }

    /** Dossier historique contenant {@code quests/} et {@code npcs/} au format NBT (lecture seule). */
    public static File legacyFolder() {
        return new File(config().getString("quests.legacy_folder", "plugins/Skript/configs/data/quêtes"));
    }

    /** Dossier des fichiers joueur historiques ({@code <uuid>.dat}) : import de la progression et données de métiers. */
    public static File legacyPlayerFolder() {
        return new File(config().getString("quests.legacy_player_folder", "plugins/Skript/configs/data/players"));
    }

    public static boolean legacyImportEnabled() {
        return config().getBoolean("quests.legacy_import", true);
    }

    public static int saveIntervalTicks() {
        return Math.max(20, config().getInt("quests.save_interval_ticks", 600));
    }

    public static boolean notifications() {
        return config().getBoolean("quests.notifications", true);
    }

    public static boolean indicatorsEnabled() {
        return config().getBoolean("quests.indicators", true);
    }

    public static boolean compassAuto() {
        return config().getBoolean("quests.compass", true);
    }

    public static double indicatorRange() {
        return config().getDouble("quests.indicator_range", 30.0);
    }

    public static String seeAllPermission() {
        return config().getString("quests.see_all_permission", "skwhy.quest.staff");
    }

    public static String adminPermission() {
        return config().getString("quests.admin_permission", "skwhy.quest.admin");
    }

    /** Fonction Skript utilisée pour fabriquer un item personnalisé à partir de son nom ({@code item_get(nom, joueur)}). */
    public static String itemFunction() {
        return config().getString("quests.item_function", "item_get");
    }

    /** Fonction Skript qui compte les exemplaires d'un item personnalisé ({@code item_count(nom, joueur)}). */
    public static String itemCountFunction() {
        return config().getString("quests.item_count_function", "item_count");
    }

    /** Fonction Skript qui ouvre le menu des quêtes ({@code f(joueur, données, cible)}). */
    public static String menuFunction() {
        return config().getString("quests.menu_function", "quest_répertoire_open");
    }

    /** Variable Skript de l'argent d'un joueur ; {@code {player}} est remplacé par le pseudo du joueur. */
    public static String moneyVariable() {
        return config().getString("quests.money_variable", "money::{player}");
    }

    /** Liste ordonnée des rangs (permission {@code group.<rang>}), du plus bas au plus haut. */
    public static List<String> ranks() {
        List<String> list = config().getStringList("quests.ranks");
        if (!list.isEmpty()) return list;
        return List.of("vagabond", "paysan", "artisan", "commerçant", "bourgeois", "écuyer", "chevalier",
            "baron", "vicomte", "comte", "marquis", "duc", "prince", "empereur");
    }

    public static String groupPermissionPrefix() {
        return config().getString("quests.group_permission_prefix", "group.");
    }

    /** Distance (en blocs) à partir de laquelle un NPC en mode {@code wait} s'arrête pour attendre le joueur. */
    public static double waitStopDistance() {
        return config().getDouble("quests.npc_wait_distance", 15.0);
    }

    public static boolean debug() {
        return config().getBoolean("quests.debug", false);
    }
}
