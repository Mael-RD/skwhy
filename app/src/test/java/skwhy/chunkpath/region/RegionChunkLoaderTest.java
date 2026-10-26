package skwhy.chunkpath.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import skwhy.chunkpath.graph.ChunkNavMesh;
import skwhy.chunkpath.nbt.NbtTag;
import skwhy.chunkpath.testsupport.TestChunks;

class RegionChunkLoaderTest {

    @TempDir
    Path folder;

    @BeforeAll
    static void server() {
        TestChunks.ensureServer();
    }

    private static byte[] compress(int type, byte[] raw) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        switch (type) {
            case 1 -> {
                try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
                    out.write(raw);
                }
            }
            case 2 -> {
                try (DeflaterOutputStream out = new DeflaterOutputStream(bytes)) {
                    out.write(raw);
                }
            }
            default -> bytes.write(raw);
        }
        return bytes.toByteArray();
    }

    /** Ecrit un fichier region contenant un seul chunk (secteur 2). */
    private void writeRegion(int regionX, int regionZ, int chunkX, int chunkZ, int compression, byte[] nbt) throws IOException {
        byte[] payload = compress(compression, nbt);
        int sectors = (payload.length + 5 + 4095) / 4096;
        try (RandomAccessFile raf = new RandomAccessFile(folder.resolve("r." + regionX + "." + regionZ + ".mca").toFile(), "rw")) {
            raf.setLength((2 + sectors) * 4096L);
            raf.seek(((chunkX & 31) + (chunkZ & 31) * 32) * 4L);
            raf.writeInt((2 << 8) | sectors);
            raf.seek(2 * 4096L);
            raf.writeInt(payload.length + 1);
            raf.writeByte(compression);
            raf.write(payload);
        }
    }

    private static byte[] flat(String status) throws IOException {
        return TestChunks.flat().bytes(status);
    }

    @Test
    void readsZlibChunk() throws IOException {
        writeRegion(0, 0, 3, 4, 2, flat("minecraft:full"));
        NbtTag root = RegionChunkLoader.loadChunkRoot(folder, 3, 4);
        assertEquals("minecraft:full", root.getString("Status", null));
        assertEquals(256, new ChunkNavMesh(root, TestChunks.uniformRules(5)).nodeCount());
    }

    @Test
    void readsGzipAndUncompressedChunks() throws IOException {
        writeRegion(0, 0, 1, 1, 1, flat("full"));
        assertEquals("full", RegionChunkLoader.loadChunkRoot(folder, 1, 1).getString("Status", null));
        writeRegion(0, 0, 2, 2, 3, flat("full"));
        assertEquals("full", RegionChunkLoader.loadChunkRoot(folder, 2, 2).getString("Status", null));
    }

    @Test
    void negativeChunkCoordinatesPickTheRightRegionFile() throws IOException {
        // chunk (-1,-1) -> region (-1,-1), entree locale (31,31)
        writeRegion(-1, -1, -1, -1, 2, flat("minecraft:full"));
        assertEquals("minecraft:full", RegionChunkLoader.loadChunkRoot(folder, -1, -1).getString("Status", null));
        // chunk (-33, 32) -> region (-2, 1)
        writeRegion(-2, 1, -33, 32, 2, flat("minecraft:full"));
        assertEquals("minecraft:full", RegionChunkLoader.loadChunkRoot(folder, -33, 32).getString("Status", null));
    }

    @Test
    void missingRegionFileMeansNotGenerated() {
        assertThrows(ChunkNotGeneratedException.class, () -> RegionChunkLoader.loadChunkRoot(folder, 0, 0));
    }

    @Test
    void chunkAbsentFromTheHeaderMeansNotGenerated() throws IOException {
        writeRegion(0, 0, 3, 4, 2, flat("full"));
        ChunkNotGeneratedException e = assertThrows(ChunkNotGeneratedException.class, () -> RegionChunkLoader.loadChunkRoot(folder, 5, 5));
        assertEquals(5, e.chunkX);
        assertEquals(5, e.chunkZ);
    }

    @Test
    void incompleteStatusMeansNotGenerated() throws IOException {
        writeRegion(0, 0, 0, 0, 2, flat("minecraft:features"));
        assertThrows(ChunkNotGeneratedException.class, () -> RegionChunkLoader.loadChunkRoot(folder, 0, 0));
    }

    @Test
    void missingStatusMeansNotGenerated() throws IOException {
        writeRegion(0, 0, 0, 0, 2, flat(null));
        assertThrows(ChunkNotGeneratedException.class, () -> RegionChunkLoader.loadChunkRoot(folder, 0, 0));
    }

    @Test
    void truncatedFileIsUnreadable() throws IOException {
        Files.write(folder.resolve("r.0.0.mca"), new byte[100]);
        assertThrows(RegionReadException.class, () -> RegionChunkLoader.loadChunkRoot(folder, 0, 0));
    }

    @Test
    void emptyFileIsUnreadable() throws IOException {
        Files.write(folder.resolve("r.0.0.mca"), new byte[0]);
        assertThrows(RegionReadException.class, () -> RegionChunkLoader.loadChunkRoot(folder, 0, 0));
    }

    @Test
    void headerPointingPastTheEndIsUnreadable() throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(folder.resolve("r.0.0.mca").toFile(), "rw")) {
            raf.setLength(8192);
            raf.writeInt((50 << 8) | 1);
        }
        assertThrows(RegionReadException.class, () -> RegionChunkLoader.loadChunkRoot(folder, 0, 0));
    }

    @Test
    void lastChunkNotPaddedToAFullSectorIsReadable() throws IOException {
        byte[] nbt = flat("full");
        writeRegion(0, 0, 0, 0, 2, nbt);
        Path file = folder.resolve("r.0.0.mca");
        // le fichier s'arrete au dernier octet du chunk, sans completer le secteur (etat d'un serveur en marche)
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.seek(2 * 4096L);
            long end = 2 * 4096L + 4 + raf.readInt();
            raf.setLength(end);
        }
        assertTrue(RegionChunkLoader.loadChunkRoot(folder, 0, 0) != null);
    }

    @Test
    void chunkDataCutShortIsUnreadable() throws IOException {
        writeRegion(0, 0, 0, 0, 2, flat("full"));
        try (RandomAccessFile raf = new RandomAccessFile(folder.resolve("r.0.0.mca").toFile(), "rw")) {
            raf.setLength(2 * 4096L + 20);
        }
        assertThrows(RegionReadException.class, () -> RegionChunkLoader.loadChunkRoot(folder, 0, 0));
    }

    @Test
    void headerPointingIntoTheHeaderIsUnreadable() throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(folder.resolve("r.0.0.mca").toFile(), "rw")) {
            raf.setLength(3 * 4096);
            raf.writeInt((1 << 8) | 1);
        }
        assertThrows(RegionReadException.class, () -> RegionChunkLoader.loadChunkRoot(folder, 0, 0));
    }

    @Test
    void lz4AndUnknownCompressionAreRejectedWithAHint() throws IOException {
        writeRegion(0, 0, 0, 0, 4, flat("full"));
        RegionReadException lz4 = assertThrows(RegionReadException.class, () -> RegionChunkLoader.loadChunkRoot(folder, 0, 0));
        assertTrue(lz4.getMessage().contains("LZ4"));
        writeRegion(0, 0, 0, 0, 9, flat("full"));
        assertThrows(RegionReadException.class, () -> RegionChunkLoader.loadChunkRoot(folder, 0, 0));
    }

    @Test
    void externalMccChunkIsRejected() throws IOException {
        writeRegion(0, 0, 0, 0, 2, flat("full"));
        try (RandomAccessFile raf = new RandomAccessFile(folder.resolve("r.0.0.mca").toFile(), "rw")) {
            raf.seek(2 * 4096L + 4);
            raf.writeByte(2 | 0x80);
        }
        assertThrows(RegionReadException.class, () -> RegionChunkLoader.loadChunkRoot(folder, 0, 0));
    }

    @Test
    void corruptPayloadIsUnreadable() throws IOException {
        writeRegion(0, 0, 0, 0, 3, new byte[] {10, 0, 0, 1, 0, 1, 'x'});
        assertThrows(RegionReadException.class, () -> RegionChunkLoader.loadChunkRoot(folder, 0, 0));
    }

    @Test
    void exceptionsCarryTheChunkCoordinates() {
        ChunkNotGeneratedException e = assertThrows(ChunkNotGeneratedException.class, () -> RegionChunkLoader.loadChunkRoot(folder, -7, 9));
        assertTrue(e.getMessage().contains("-7") && e.getMessage().contains("9"));
    }
}
