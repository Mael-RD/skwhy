package skwhy.chunkpath.testsupport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.mockbukkit.mockbukkit.MockBukkit;

import skwhy.chunkpath.block.PathCostRules;
import skwhy.chunkpath.nbt.NbtReader;
import skwhy.chunkpath.nbt.NbtTag;

/**
 * Fabrique des chunks NBT (format >= 1.18) en memoire, puis les relit avec {@link NbtReader} exactement comme
 * pour un fichier region : aucun serveur n'est necessaire. Un chunk est decrit par un {@link Builder}
 * (blocs poses par coordonnees locales x 0-15, y absolu, z 0-15).
 */
public final class TestChunks {

    private static boolean mocked;

    private TestChunks() {
    }

    /** Une seule regle sans bloc ni tag (regle par defaut de la liste) : tout bloc marchable coute {@code cost}. */
    public static PathCostRules uniformRules(int cost) {
        return new PathCostRules(List.of(new PathCostRules.CostRule(List.of(), List.of(), cost)));
    }

    /** Demarre un serveur simule (Material, Tag...) pour les tests qui classent des blocs. A appeler une fois. */
    public static synchronized void ensureServer() {
        if (!mocked) {
            MockBukkit.mock();
            mocked = true;
        }
    }

    public static final class Builder {
        private final int minSection, maxSection;
        private final Map<Integer, String> blocks = new LinkedHashMap<>();

        Builder(int minSection, int maxSection) {
            this.minSection = minSection;
            this.maxSection = maxSection;
        }

        private static int key(int x, int y, int z) {
            return ((y + 64) * 16 + z) * 16 + x;
        }

        public Builder set(int x, int y, int z, String name) {
            blocks.put(key(x, y, z), name);
            return this;
        }

        public Builder fill(int x1, int y1, int z1, int x2, int y2, int z2, String name) {
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++)
                    for (int x = x1; x <= x2; x++)
                        set(x, y, z, name);
            return this;
        }

        public Builder air(int x1, int y1, int z1, int x2, int y2, int z2) {
            return fill(x1, y1, z1, x2, y2, z2, "minecraft:air");
        }

        public NbtTag build() {
            try {
                return NbtReader.readRoot(new DataInputStream(new ByteArrayInputStream(bytes())));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private byte[] bytes() throws IOException {
            return bytes(null);
        }

        /** NBT brut non compresse du chunk, avec un tag {@code Status} si {@code status} n'est pas nul. */
        public byte[] bytes(String status) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(NbtTag.COMPOUND);
            out.writeUTF("");
            if (status != null) {
                out.writeByte(NbtTag.STRING);
                out.writeUTF("Status");
                out.writeUTF(status);
            }
            out.writeByte(NbtTag.LIST);
            out.writeUTF("sections");
            out.writeByte(NbtTag.COMPOUND);
            out.writeInt(maxSection - minSection + 1);
            for (int s = minSection; s <= maxSection; s++) {
                writeSection(out, s);
            }
            out.writeByte(NbtTag.END);
            return bytes.toByteArray();
        }

        private void writeSection(DataOutputStream out, int section) throws IOException {
            String[] cells = new String[4096];
            List<String> palette = new ArrayList<>();
            palette.add("minecraft:air");
            for (int i = 0; i < 4096; i++) {
                int lx = i & 15, lz = (i >> 4) & 15, ly = (i >> 8) & 15;
                String name = blocks.getOrDefault(key(lx, section * 16 + ly, lz), "minecraft:air");
                cells[i] = name;
                if (!palette.contains(name)) palette.add(name);
            }
            out.writeByte(NbtTag.BYTE);
            out.writeUTF("Y");
            out.writeByte(section);
            out.writeByte(NbtTag.COMPOUND);
            out.writeUTF("block_states");
            out.writeByte(NbtTag.LIST);
            out.writeUTF("palette");
            out.writeByte(NbtTag.COMPOUND);
            out.writeInt(palette.size());
            for (String entry : palette) {
                // "minecraft:oak_trapdoor[half=top,open=false]" : nom + propriétés d'état
                int bracket = entry.indexOf('[');
                String name = bracket < 0 ? entry : entry.substring(0, bracket);
                out.writeByte(NbtTag.STRING);
                out.writeUTF("Name");
                out.writeUTF(name);
                if (bracket >= 0) {
                    out.writeByte(NbtTag.COMPOUND);
                    out.writeUTF("Properties");
                    for (String property : entry.substring(bracket + 1, entry.length() - 1).split(",")) {
                        String[] kv = property.split("=");
                        out.writeByte(NbtTag.STRING);
                        out.writeUTF(kv[0]);
                        out.writeUTF(kv[1]);
                    }
                    out.writeByte(NbtTag.END);
                }
                out.writeByte(NbtTag.END);
            }
            if (palette.size() > 1) {
                int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
                int perLong = 64 / bits;
                long[] data = new long[(4096 + perLong - 1) / perLong];
                for (int i = 0; i < 4096; i++) {
                    long index = palette.indexOf(cells[i]);
                    data[i / perLong] |= index << ((i % perLong) * bits);
                }
                out.writeByte(NbtTag.LONG_ARRAY);
                out.writeUTF("data");
                out.writeInt(data.length);
                for (long l : data) out.writeLong(l);
            }
            out.writeByte(NbtTag.END); // block_states
            out.writeByte(NbtTag.END); // section
        }
    }

    /** Chunk sur les sections 0..{@code topSection} (y 0 a 16*(topSection+1)-1). */
    public static Builder builder(int topSection) {
        return new Builder(0, topSection);
    }

    /** Chunk plat : sol de pierre en y=0, air au-dessus (cellules marchables en y=1). */
    public static Builder flat() {
        return builder(0).fill(0, 0, 0, 15, 0, 15, "minecraft:stone");
    }

    public static NbtTag flatChunk() {
        return flat().build();
    }

    public static NbtTag chunk(Consumer<Builder> edit) {
        Builder b = flat();
        edit.accept(b);
        return b.build();
    }
}
