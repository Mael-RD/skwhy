package skwhy.chunkpath.nbt;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parseur NBT minimal en lecture seule. Suffisant pour lire n'importe quel chunk
 * moderne (post-1.18) : pas de tag "Level", les sections sont directement sous "sections".
 */
public final class NbtReader {

    private NbtReader() {
    }

    /**
     * Lit le tag racine d'un blob NBT déjà décompressé (le premier octet est le type
     * du tag racine, suivi de son nom, comme pour n'importe quel tag nommé).
     */
    public static NbtTag readRoot(DataInputStream in) throws IOException {
        byte type = in.readByte();
        if (type == NbtTag.END) {
            throw new IOException("NBT vide / tag racine manquant");
        }
        readUtf(in); // nom du tag racine, ignoré (habituellement vide)
        return readPayload(in, type);
    }

    private static NbtTag readPayload(DataInputStream in, byte type) throws IOException {
        return switch (type) {
            case NbtTag.BYTE -> new NbtTag(NbtTag.BYTE, in.readByte());
            case NbtTag.SHORT -> new NbtTag(NbtTag.SHORT, in.readShort());
            case NbtTag.INT -> new NbtTag(NbtTag.INT, in.readInt());
            case NbtTag.LONG -> new NbtTag(NbtTag.LONG, in.readLong());
            case NbtTag.FLOAT -> new NbtTag(NbtTag.FLOAT, in.readFloat());
            case NbtTag.DOUBLE -> new NbtTag(NbtTag.DOUBLE, in.readDouble());
            case NbtTag.BYTE_ARRAY -> {
                int len = in.readInt();
                byte[] arr = new byte[len];
                in.readFully(arr);
                yield new NbtTag(NbtTag.BYTE_ARRAY, arr);
            }
            case NbtTag.STRING -> new NbtTag(NbtTag.STRING, readUtf(in));
            case NbtTag.LIST -> readList(in);
            case NbtTag.COMPOUND -> readCompound(in);
            case NbtTag.INT_ARRAY -> {
                int len = in.readInt();
                int[] arr = new int[len];
                for (int i = 0; i < len; i++) arr[i] = in.readInt();
                yield new NbtTag(NbtTag.INT_ARRAY, arr);
            }
            case NbtTag.LONG_ARRAY -> {
                int len = in.readInt();
                long[] arr = new long[len];
                for (int i = 0; i < len; i++) arr[i] = in.readLong();
                yield new NbtTag(NbtTag.LONG_ARRAY, arr);
            }
            default -> throw new IOException("Type de tag NBT inconnu: " + type);
        };
    }

    private static NbtTag readCompound(DataInputStream in) throws IOException {
        Map<String, NbtTag> map = new LinkedHashMap<>();
        while (true) {
            byte type = in.readByte();
            if (type == NbtTag.END) break;
            String name = readUtf(in);
            map.put(name, readPayload(in, type));
        }
        return NbtTag.compound(map);
    }

    private static NbtTag readList(DataInputStream in) throws IOException {
        byte elementType = in.readByte();
        int len = in.readInt();
        List<NbtTag> list = new ArrayList<>(Math.max(0, len));
        for (int i = 0; i < len; i++) {
            list.add(elementType == NbtTag.END ? new NbtTag(NbtTag.END, null) : readPayload(in, elementType));
        }
        return NbtTag.list(elementType, list);
    }

    private static String readUtf(DataInputStream in) throws IOException {
        return in.readUTF();
    }
}
