package skwhy.chunkpath.nbt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

class NbtReaderTest {

    @FunctionalInterface
    interface Body {
        void write(DataOutputStream out) throws IOException;
    }

    static NbtTag read(Body body) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        body.write(new DataOutputStream(bytes));
        return NbtReader.readRoot(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }

    private static void named(DataOutputStream out, byte type, String name) throws IOException {
        out.writeByte(type);
        out.writeUTF(name);
    }

    @Test
    void readsAllScalarTypes() throws IOException {
        NbtTag root = read(out -> {
            named(out, NbtTag.COMPOUND, "");
            named(out, NbtTag.BYTE, "b");
            out.writeByte(-3);
            named(out, NbtTag.SHORT, "s");
            out.writeShort(300);
            named(out, NbtTag.INT, "i");
            out.writeInt(70000);
            named(out, NbtTag.LONG, "l");
            out.writeLong(5_000_000_000L);
            named(out, NbtTag.FLOAT, "f");
            out.writeFloat(1.5f);
            named(out, NbtTag.DOUBLE, "d");
            out.writeDouble(2.5);
            named(out, NbtTag.STRING, "str");
            out.writeUTF("héllo");
            out.writeByte(NbtTag.END);
        });
        assertTrue(root.isCompound());
        assertEquals((byte) -3, root.getByte("b", (byte) 0));
        assertEquals(300, root.getInt("s", 0));
        assertEquals(70000, root.getInt("i", 0));
        assertEquals((int) 5_000_000_000L, root.getInt("l", 0));
        assertEquals("héllo", root.getString("str", "x"));
        assertEquals(1.5f, root.get("f").value);
        assertEquals(2.5, root.get("d").value);
    }

    @Test
    void defaultsForMissingOrWrongTypes() throws IOException {
        NbtTag root = read(out -> {
            named(out, NbtTag.COMPOUND, "");
            named(out, NbtTag.STRING, "name");
            out.writeUTF("x");
            out.writeByte(NbtTag.END);
        });
        assertEquals(42, root.getInt("absent", 42));
        assertEquals(42, root.getInt("name", 42));
        assertEquals("def", root.getString("absent", "def"));
        assertEquals((byte) 9, root.getByte("name", (byte) 9));
        assertNull(root.getLongArray("absent"));
        assertTrue(root.getList("absent").isEmpty());
        assertFalse(root.has("absent"));
        assertTrue(root.has("name"));
        assertNull(root.get("absent"));
    }

    @Test
    void readsArraysListsAndNestedCompounds() throws IOException {
        NbtTag root = read(out -> {
            named(out, NbtTag.COMPOUND, "root");
            named(out, NbtTag.LONG_ARRAY, "data");
            out.writeInt(2);
            out.writeLong(1L);
            out.writeLong(-1L);
            named(out, NbtTag.INT_ARRAY, "ints");
            out.writeInt(2);
            out.writeInt(7);
            out.writeInt(8);
            named(out, NbtTag.BYTE_ARRAY, "bytes");
            out.writeInt(3);
            out.write(new byte[] {1, 2, 3});
            named(out, NbtTag.LIST, "sections");
            out.writeByte(NbtTag.COMPOUND);
            out.writeInt(2);
            named(out, NbtTag.BYTE, "Y");
            out.writeByte(-4);
            out.writeByte(NbtTag.END);
            named(out, NbtTag.BYTE, "Y");
            out.writeByte(5);
            out.writeByte(NbtTag.END);
            named(out, NbtTag.COMPOUND, "inner");
            named(out, NbtTag.INT, "v");
            out.writeInt(11);
            out.writeByte(NbtTag.END);
            out.writeByte(NbtTag.END);
        });
        assertArrayEquals(new long[] {1L, -1L}, root.getLongArray("data"));
        assertArrayEquals(new int[] {7, 8}, (int[]) root.get("ints").value);
        assertArrayEquals(new byte[] {1, 2, 3}, (byte[]) root.get("bytes").value);
        assertEquals(2, root.getList("sections").size());
        assertEquals(-4, root.getList("sections").get(0).getByte("Y", (byte) 0));
        assertEquals(5, root.getList("sections").get(1).getByte("Y", (byte) 0));
        assertEquals(11, root.get("inner").getInt("v", 0));
    }

    @Test
    void emptyListHasNoElements() throws IOException {
        NbtTag root = read(out -> {
            named(out, NbtTag.COMPOUND, "");
            named(out, NbtTag.LIST, "l");
            out.writeByte(NbtTag.END);
            out.writeInt(0);
            out.writeByte(NbtTag.END);
        });
        assertTrue(root.getList("l").isEmpty());
    }

    @Test
    void emptyRootIsRejected() {
        assertThrows(IOException.class, () -> read(out -> out.writeByte(NbtTag.END)));
    }

    @Test
    void unknownTagTypeIsRejected() {
        assertThrows(IOException.class, () -> read(out -> {
            named(out, NbtTag.COMPOUND, "");
            named(out, (byte) 99, "x");
        }));
    }

    @Test
    void truncatedStreamFails() {
        assertThrows(IOException.class, () -> read(out -> {
            named(out, NbtTag.COMPOUND, "");
            named(out, NbtTag.INT, "i");
            out.writeByte(0);
        }));
    }

    @Test
    void accessorsOnANonCompoundTagAreSafe() throws IOException {
        NbtTag root = read(out -> {
            named(out, NbtTag.STRING, "");
            out.writeUTF("seul");
        });
        assertFalse(root.isCompound());
        assertNull(root.get("a"));
        assertFalse(root.has("a"));
        assertTrue(root.getList("a").isEmpty());
        assertEquals(3, root.getInt("a", 3));
    }
}
