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

    /** Dossier historique contenant {@code quests/} et {@code npcs/} au format NBT (lecture seule). */
    public static File legacyFolder() {
        return new File(config().getString("quests.legacy_folder", "plugins/Skript/configs/data/quêtes"));
    }

    /** Dossier des fichiers de données des joueurs ({@code <uuid>.dat}) : le NBT custom des joueurs déconnectés y est lu et écrit. */
    public static File playerFolder() {
        return new File(config().getString("quests.player_folder", "world/players/data"));
    }

    public static boolean legacyImportEnabled() {
        return config().getBoolean("quests.legacy_import", true);
    }

    /** Convertit les anciens fichiers NBT en YAML au chargement (un seul format) ; les .nbt sont renommés en .nbt.migrated. */
    public static boolean legacyMigrate() {
        return config().getBoolean("quests.legacy_migrate", true);
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

    /** Facteur de vitesse maximal d'un NPC en mode {@code wait} quand il est loin derrière le joueur. */
    public static double npcMaxSpeedFactor() {
        return Math.max(1.0, config().getDouble("quests.npc_max_speed_factor", 1.6));
    }

    /** Variables Skript de règles de coût proposées à l'action {@code move} ({@code a::b} = {@code {a::b::*}}). */
    public static List<String> pathfindingRules() {
        return SkWhy.getInstance() == null ? List.of() : config().getStringList("quests.pathfinding_rules");
    }

    public static boolean debug() {
        return config().getBoolean("quests.debug", false);
    }
}
