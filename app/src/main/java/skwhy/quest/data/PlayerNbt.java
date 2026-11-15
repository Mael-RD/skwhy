package skwhy.quest.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer;
import org.bukkit.entity.Player;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import skwhy.SkWhy;
import skwhy.quest.QuestSettings;

/**
 * Accès au NBT custom (SkBee, {@code BukkitValues;skbee-custom}) d'un joueur : celui du joueur connecté est modifié en
 * place (le serveur le sauvegarde avec le joueur), celui d'un joueur déconnecté est lu et écrit dans son fichier
 * {@code <dossier des joueurs>/<uuid>.dat}. C'est là que les scripts (fonctions {@code player_nbt}) et le module
 * de quêtes partagent la progression ({@code quest;liste;<quête>;...}).
 */
public final class PlayerNbt {

    private static final String CUSTOM = "skbee-custom";

    private PlayerNbt() {}

    /** Le NBT custom d'un joueur et la façon de l'enregistrer. */
    public static final class Access {
        public final CompoundTag custom;
        private final CompoundTag fileRoot;
        private final File file;

        Access(CompoundTag custom, CompoundTag fileRoot, File file) {
            this.custom = custom;
            this.fileRoot = fileRoot;
            this.file = file;
        }

        /** Enregistre (sans effet pour un joueur connecté : son NBT est déjà le vrai). */
        public boolean commit() {
            if (fileRoot == null) return true;
            try {
                Path.write(fileRoot, file);
                return true;
            } catch (IOException e) {
                SkWhy.getInstance().getLogger().warning("[Quest] Impossible d'écrire " + file.getPath() + " : " + e);
                return false;
            }
        }
    }

    private static final class Path {
        static void write(CompoundTag root, File file) throws IOException {
            File tmp = new File(file.getParentFile(), file.getName() + ".skwhy-tmp");
            NbtIo.writeCompressed(root, tmp.toPath());
            Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static File fileOf(UUID uuid) {
        return new File(QuestSettings.playerFolder(), uuid + ".dat");
    }

    /** @return {@code true} si le joueur est connecté ou a un fichier de données. */
    public static boolean exists(UUID uuid) {
        return Bukkit.getPlayer(uuid) != null || fileOf(uuid).isFile();
    }

    /**
     * Ouvre le NBT custom du joueur. Sans {@code create}, renvoie {@code null} si le joueur n'a pas de données ou pas de
     * NBT custom.
     */
    public static Access open(UUID uuid, boolean create) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            CraftPersistentDataContainer pdc = (CraftPersistentDataContainer) online.getPersistentDataContainer();
            Tag tag = pdc.getRaw().get(CUSTOM);
            if (tag instanceof CompoundTag c) return new Access(c, null, null);
            if (!create) return null;
            CompoundTag c = new CompoundTag();
            pdc.put(CUSTOM, c);
            return new Access(c, null, null);
        }
        File file = fileOf(uuid);
        if (!file.isFile()) return null;
        try {
            CompoundTag root = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
            CompoundTag values = root.getCompound("BukkitValues").orElse(null);
            CompoundTag custom = values == null ? null : values.getCompound(CUSTOM).orElse(null);
            if (custom == null) {
                if (!create) return null;
                if (values == null) {
                    values = new CompoundTag();
                    root.put("BukkitValues", values);
                }
                custom = new CompoundTag();
                values.put(CUSTOM, custom);
            }
            return new Access(custom, root, file);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Empreinte du sous-arbre {@code quest} : change dès que quelqu'un d'autre (un script) modifie les données.
     * {@link Long#MIN_VALUE} s'il n'y a aucun NBT à suivre.
     */
    public static long stamp(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            CraftPersistentDataContainer pdc = (CraftPersistentDataContainer) online.getPersistentDataContainer();
            Tag custom = pdc.getRaw().get(CUSTOM);
            if (custom instanceof CompoundTag c) {
                Tag quest = c.get("quest");
                return quest == null ? 1 : quest.hashCode();
            }
            return 1;
        }
        File file = fileOf(uuid);
        return file.isFile() ? file.lastModified() * 31 + file.length() : Long.MIN_VALUE;
    }

    /** Valeur (arbre {@link Map} / {@link List} / {@link String} / {@link Number}) du chemin {@code a;b;c}, ou {@code null}. */
    public static Object path(UUID uuid, String path) {
        Access access = open(uuid, false);
        if (access == null) return null;
        Tag tag = access.custom;
        for (String part : path.split(";")) {
            if (tag instanceof CompoundTag c) tag = c.get(part);
            else if (tag instanceof ListTag l) {
                try { tag = l.get(Integer.parseInt(part)); } catch (RuntimeException e) { return null; }
            } else return null;
            if (tag == null) return null;
        }
        return toTree(tag);
    }

    // ── Tag <-> arbre ───────────────────────────────────────────────────────

    /** Un {@link ByteTag} reste un {@link Byte} (booléen), un compteur est un {@link Integer}. */
    public static Object toTree(Tag tag) {
        if (tag == null) return null;
        if (tag instanceof CompoundTag c) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<String, Tag> e : c.entrySet()) out.put(e.getKey(), toTree(e.getValue()));
            return out;
        }
        if (tag instanceof ListTag l) {
            List<Object> out = new ArrayList<>();
            for (Tag t : l) out.add(toTree(t));
            return out;
        }
        if (tag instanceof StringTag s) return s.value();
        if (tag instanceof ByteTag b) return b.value();
        if (tag instanceof NumericTag n) {
            return switch (tag.getId()) {
                case 2 -> n.shortValue();
                case 3 -> n.intValue();
                case 4 -> n.longValue();
                case 5 -> n.floatValue();
                default -> n.doubleValue();
            };
        }
        return null;
    }

    /** Tag d'une valeur scalaire de variable : booléen = octet, entier, décimal ou texte. */
    public static Tag scalarTag(Object value) {
        if (value instanceof Boolean b) return ByteTag.valueOf(b);
        if (value instanceof Double || value instanceof Float) return DoubleTag.valueOf(((Number) value).doubleValue());
        if (value instanceof Number n) return IntTag.valueOf(n.intValue());
        return StringTag.valueOf(String.valueOf(value));
    }
}
