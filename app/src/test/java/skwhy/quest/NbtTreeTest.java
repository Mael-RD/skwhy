package skwhy.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;

class NbtTreeTest {

    /** {name:"Quête", priority:3, ratio:1.5d, ok:1b, steps:[{text:"a"},{text:"b"}], ids:[I;4,5]} */
    private static byte[] sample() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10);
        out.writeUTF("");
        out.writeByte(8);
        out.writeUTF("name");
        out.writeUTF("Quête");
        out.writeByte(3);
        out.writeUTF("priority");
        out.writeInt(3);
        out.writeByte(6);
        out.writeUTF("ratio");
        out.writeDouble(1.5);
        out.writeByte(1);
        out.writeUTF("ok");
        out.writeByte(1);
        out.writeByte(9);
        out.writeUTF("steps");
        out.writeByte(10);
        out.writeInt(2);
        for (String text : new String[]{"a", "b"}) {
            out.writeByte(8);
            out.writeUTF("text");
            out.writeUTF(text);
            out.writeByte(0);
        }
        out.writeByte(11);
        out.writeUTF("ids");
        out.writeInt(2);
        out.writeInt(4);
        out.writeInt(5);
        out.writeByte(0);
        return bytes.toByteArray();
    }

    private static byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
            gz.write(raw);
        }
        return bytes.toByteArray();
    }

    private static void check(Map<String, Object> tree) {
        assertEquals("Quête", tree.get("name"));
        assertEquals(3, tree.get("priority"));
        assertEquals(1.5, tree.get("ratio"));
        assertInstanceOf(Byte.class, tree.get("ok"));
        assertEquals("b", Tree.path(tree, "steps[1];text"));
        assertEquals(List.of(4, 5), tree.get("ids"));
    }

    @Test
    void readsUncompressedNbt() throws IOException {
        check(NbtTree.read(sample()));
    }

    @Test
    void readsGzipNbt() throws IOException {
        check(NbtTree.read(gzip(sample())));
    }

    @Test
    void anythingButACompoundIsRejected() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(9);
        out.writeUTF("");
        assertNull(NbtTree.read(bytes.toByteArray()));
    }

    @Test
    void missingFileGivesNull() {
        assertNull(NbtTree.read(new java.io.File("does-not-exist.nbt")));
    }
}
