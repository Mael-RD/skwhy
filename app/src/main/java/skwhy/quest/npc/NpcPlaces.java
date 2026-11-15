package skwhy.quest.npc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import skwhy.SkWhy;

/**
 * Enregistrement persistant des positions de chaque NPC ({@code plugins/SkWhy/quest-npc-positions.yml}) : pour chaque NPC et chaque
 * numéro de position (1, 2, 3...), l'endroit où il se tient. Il ne dépend d'aucune entité chargée : la boussole, les déplacements
 * et l'éditeur connaissent la position d'un NPC même quand son entité est dans un chunk non chargé ou n'existe pas (entité custom fausse).
 * Une liaison ({@code bind}) y inscrit l'endroit de l'entité liée.
 */
public final class NpcPlaces {

    /** Un endroit où se tient un NPC. */
    public record Place(String world, double x, double y, double z, float yaw, float pitch) {

        public static Place of(Location at) {
            return new Place(at.getWorld() == null ? "" : at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch());
        }

        /** L'endroit comme {@link Location}, {@code null} si le monde n'est pas chargé. */
        public Location toLocation() {
            org.bukkit.World w = org.bukkit.Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, pitch);
        }
    }

    /** Séparateur de chemin YAML qui ne peut pas figurer dans un id de NPC (qui peut contenir des points). */
    private static final char SEP = '\u0001';

    private static final Map<String, TreeMap<Integer, Place>> PLACES = new ConcurrentHashMap<>();

    private NpcPlaces() {}

    /** Fichier imposé (tests) ; sinon {@code plugins/SkWhy/quest-npc-positions.yml}. */
    private static volatile File fileOverride;

    static void useFile(File file) {
        fileOverride = file;
    }

    /** Oublie tout ce qui est en mémoire (le fichier n'est pas touché). */
    static void clearMemory() {
        PLACES.clear();
    }

    private static File file() {
        File override = fileOverride;
        return override != null ? override : new File(SkWhy.getInstance().getDataFolder(), "quest-npc-positions.yml");
    }

    public static synchronized void load() {
        PLACES.clear();
        File file = file();
        if (!file.isFile()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().pathSeparator(SEP);
        try {
            yaml.load(file);
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            SkWhy.getInstance().getLogger().warning("[Quest] Positions des NPC illisibles (" + file.getName() + ") : " + e.getMessage());
            return;
        }
        for (String npc : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(npc);
            if (section == null) continue;
            TreeMap<Integer, Place> places = new TreeMap<>();
            for (String key : section.getKeys(false)) {
                ConfigurationSection p = section.getConfigurationSection(key);
                if (p == null) continue;
                try {
                    places.put(Integer.parseInt(key), new Place(p.getString("world", ""), p.getDouble("x"), p.getDouble("y"), p.getDouble("z"),
                        (float) p.getDouble("yaw"), (float) p.getDouble("pitch")));
                } catch (NumberFormatException ignored) {
                    // position illisible : ignorée
                }
            }
            if (!places.isEmpty()) PLACES.put(npc, places);
        }
    }

    private static synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().pathSeparator(SEP);
        for (Map.Entry<String, TreeMap<Integer, Place>> npc : new TreeMap<>(PLACES).entrySet()) {
            for (Map.Entry<Integer, Place> e : npc.getValue().entrySet()) {
                String base = npc.getKey() + SEP + e.getKey() + SEP;
                Place p = e.getValue();
                yaml.set(base + "world", p.world());
                yaml.set(base + "x", p.x());
                yaml.set(base + "y", p.y());
                yaml.set(base + "z", p.z());
                yaml.set(base + "yaw", (double) p.yaw());
                yaml.set(base + "pitch", (double) p.pitch());
            }
        }
        File file = file();
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            SkWhy.getInstance().getLogger().warning("[Quest] Positions des NPC non enregistrées : " + e.getMessage());
        }
    }

    /** L'endroit enregistré de ce NPC à cette position, ou {@code null}. */
    public static Place get(String npcId, int position) {
        TreeMap<Integer, Place> places = PLACES.get(npcId);
        return places == null ? null : places.get(position);
    }

    /** Positions enregistrées du NPC, croissantes. */
    public static List<Integer> positions(String npcId) {
        TreeMap<Integer, Place> places = PLACES.get(npcId);
        return places == null ? List.of() : new ArrayList<>(places.keySet());
    }

    /** Enregistre (ou remplace) l'endroit d'une position ; fichier réécrit seulement si cela change quelque chose. */
    public static synchronized boolean set(String npcId, int position, Place place) {
        TreeMap<Integer, Place> places = PLACES.computeIfAbsent(npcId, k -> new TreeMap<>());
        if (place.equals(places.put(position, place))) return false;
        save();
        return true;
    }

    /** Oublie l'endroit d'une position. */
    public static synchronized boolean remove(String npcId, int position) {
        TreeMap<Integer, Place> places = PLACES.get(npcId);
        if (places == null || places.remove(position) == null) return false;
        if (places.isEmpty()) PLACES.remove(npcId);
        save();
        return true;
    }
}
