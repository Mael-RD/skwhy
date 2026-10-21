package skwhy.quest;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Lecteur NBT minimal (fichiers {@code .nbt} / {@code .dat}, compressés en gzip ou non) qui produit un arbre de
 * {@link Map} / {@link List} / {@link String} / {@link Number}. Les fichiers de quêtes et de NPC historiques sont
 * lus tels quels, sans conversion préalable.
 */
public final class NbtTree {

    private NbtTree() {}

    /** @return l'arbre racine, ou {@code null} si le fichier est absent ou illisible. */
    public static Map<String, Object> read(File file) {
        if (file == null || !file.isFile()) return null;
        try {
            byte[] raw = Files.readAllBytes(file.toPath());
            return read(raw);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static Map<String, Object> read(byte[] raw) throws IOException {
        byte[] data = raw;
        if (raw.length > 2 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B) {
            try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                data = in.readAllBytes();
            }
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            byte type = in.readByte();
            if (type != 10) return null;
            in.readUTF();
            return compound(in);
        }
    }

    private static Map<String, Object> compound(DataInputStream in) throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        while (true) {
            byte type = in.readByte();
            if (type == 0) return out;
            String key = in.readUTF();
            out.put(key, payload(in, type));
        }
    }

    private static Object payload(DataInputStream in, byte type) throws IOException {
        switch (type) {
            case 1: return in.readByte();
            case 2: return (int) in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: {
                int n = in.readInt();
                List<Object> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add((int) in.readByte());
                return l;
            }
            case 8: return in.readUTF();
            case 9: {
                byte inner = in.readByte();
                int n = in.readInt();
                List<Object> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add(payload(in, inner));
                return l;
            }
            case 10: return compound(in);
            case 11: {
                int n = in.readInt();
                List<Object> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add(in.readInt());
                return l;
            }
            case 12: {
                int n = in.readInt();
                List<Object> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add(in.readLong());
                return l;
            }
            default: throw new IOException("Tag NBT inconnu : " + type);
        }
    }
}
