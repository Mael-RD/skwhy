package skwhy.chunkpath.nbt;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Représentation minimale d'un tag NBT (juste ce qu'il faut pour lire des chunks).
 * Pas de support d'écriture : on ne fait que de la lecture de fichiers région existants.
 */
public final class NbtTag {

    public static final byte END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4,
            FLOAT = 5, DOUBLE = 6, BYTE_ARRAY = 7, STRING = 8, LIST = 9,
            COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

    public final byte type;
    public final Object value;

    NbtTag(byte type, Object value) {
        this.type = type;
        this.value = value;
    }

    static NbtTag compound(Map<String, NbtTag> map) {
        return new NbtTag(COMPOUND, map);
    }

    static NbtTag list(byte elementType, List<NbtTag> list) {
        return new NbtTag(LIST, list);
    }

    @SuppressWarnings("unchecked")
    private Map<String, NbtTag> asCompound() {
        if (type != COMPOUND) return Collections.emptyMap();
        return (Map<String, NbtTag>) value;
    }

    @SuppressWarnings("unchecked")
    private List<NbtTag> asList() {
        if (type != LIST) return Collections.emptyList();
        return (List<NbtTag>) value;
    }

    public boolean isCompound() {
        return type == COMPOUND;
    }

    public NbtTag get(String key) {
        return asCompound().get(key);
    }

    public boolean has(String key) {
        return asCompound().containsKey(key);
    }

    public List<NbtTag> getList(String key) {
        NbtTag t = get(key);
        return t == null ? Collections.emptyList() : t.asList();
    }

    public String getString(String key, String def) {
        NbtTag t = get(key);
        return (t != null && t.type == STRING) ? (String) t.value : def;
    }

    public int getInt(String key, int def) {
        NbtTag t = get(key);
        if (t == null) return def;
        return switch (t.type) {
            case BYTE -> (Byte) t.value;
            case SHORT -> (Short) t.value;
            case INT -> (Integer) t.value;
            case LONG -> (int) (long) (Long) t.value;
            default -> def;
        };
    }

    public byte getByte(String key, byte def) {
        NbtTag t = get(key);
        if (t == null) return def;
        return switch (t.type) {
            case BYTE -> (Byte) t.value;
            case SHORT -> (byte) (short) (Short) t.value;
            case INT -> (byte) (int) (Integer) t.value;
            default -> def;
        };
    }

    public long[] getLongArray(String key) {
        NbtTag t = get(key);
        return (t != null && t.type == LONG_ARRAY) ? (long[]) t.value : null;
    }

    @Override
    public String toString() {
        return "NbtTag{type=" + type + ", value=" + value + "}";
    }
}
