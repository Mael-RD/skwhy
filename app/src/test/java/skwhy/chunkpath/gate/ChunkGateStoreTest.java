package skwhy.chunkpath.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import skwhy.chunkpath.Direction;

class ChunkGateStoreTest {

    @TempDir
    Path folder;

    private static ChunkGateData sample(int cx, int cz) {
        List<int[]> members = new ArrayList<>(List.of(new int[] {cx * 16 + 15, 64, cz * 16 + 3}, new int[] {cx * 16 + 15, 65, cz * 16 + 4}));
        Gate east = new Gate(cx, cz, 0, Direction.EAST, members, members.get(0));
        east.deadEndCandidate = true;
        east.internalDistances.put(1, 35);
        east.neighborLinks.add((cx + 1) + "," + cz + ",0");
        List<int[]> north = new ArrayList<>(List.of(new int[] {cx * 16 + 2, 70, cz * 16}));
        Gate n = new Gate(cx, cz, 1, Direction.NORTH, north, north.get(0));
        n.internalDistances.put(0, 35);
        return new ChunkGateData(cx, cz, List.of(east, n));
    }

    private static void assertSame(ChunkGateData expected, ChunkGateData actual) {
        assertEquals(expected.chunkX, actual.chunkX);
        assertEquals(expected.chunkZ, actual.chunkZ);
        assertEquals(expected.gates().size(), actual.gates().size());
        for (Gate e : expected.gates()) {
            Gate a = actual.byId(e.id);
            assertNotNull(a, "gate " + e.id);
            assertEquals(e.edge, a.edge);
            assertEquals(e.deadEndCandidate, a.deadEndCandidate);
            assertEquals(e.internalDistances, a.internalDistances);
            assertEquals(e.neighborLinks, a.neighborLinks);
            assertEquals(e.memberBlocksWorld.size(), a.memberBlocksWorld.size());
            for (int i = 0; i < e.memberBlocksWorld.size(); i++) {
                assertEquals(List.of(e.memberBlocksWorld.get(i)[0], e.memberBlocksWorld.get(i)[1], e.memberBlocksWorld.get(i)[2]),
                        List.of(a.memberBlocksWorld.get(i)[0], a.memberBlocksWorld.get(i)[1], a.memberBlocksWorld.get(i)[2]));
            }
            assertEquals(List.of(e.representativeWorld[0], e.representativeWorld[1], e.representativeWorld[2]),
                    List.of(a.representativeWorld[0], a.representativeWorld[1], a.representativeWorld[2]));
        }
    }

    @Test
    void roundTrip() {
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            assertFalse(store.exists("world", 2, 3));
            assertNull(store.load("world", 2, 3));
            store.save("world", sample(2, 3));
            assertTrue(store.exists("world", 2, 3));
            assertSame(sample(2, 3), store.load("world", 2, 3));
        }
    }

    @Test
    void negativeChunkCoordinates() {
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            store.save("world", sample(-5, -7));
            store.save("world", sample(-5, 7));
            store.save("world", sample(5, -7));
            assertSame(sample(-5, -7), store.load("world", -5, -7));
            assertSame(sample(-5, 7), store.load("world", -5, 7));
            assertSame(sample(5, -7), store.load("world", 5, -7));
            assertFalse(store.exists("world", 5, 7));
        }
    }

    @Test
    void dataSurvivesReopening() {
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            store.save("world", sample(1, 1));
            store.save("world", sample(2, 2));
        }
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            assertSame(sample(1, 1), store.load("world", 1, 1));
            assertEquals(2, store.all("world").size());
        }
    }

    @Test
    void savingAgainReplacesTheRecord() {
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            store.save("world", sample(1, 1));
            ChunkGateData changed = sample(1, 1);
            changed.prune(Set.of(1));
            store.save("world", changed);
            assertEquals(1, store.load("world", 1, 1).gates().size());
            assertEquals(1, store.all("world").size());
        }
    }

    @Test
    void identicalSaveDoesNotGrowTheFile() throws IOException {
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            store.save("world", sample(1, 1));
            long size = Files.size(folder.resolve("chunkpath-data").resolve("world.db"));
            store.save("world", sample(1, 1));
            store.save("world", sample(1, 1));
            assertEquals(size, Files.size(folder.resolve("chunkpath-data").resolve("world.db")));
        }
    }

    @Test
    void deleteReportsWhetherTheRecordExisted() {
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            assertFalse(store.delete("world", 1, 1));
            store.save("world", sample(1, 1));
            assertTrue(store.delete("world", 1, 1));
            assertFalse(store.exists("world", 1, 1));
            assertFalse(store.delete("world", 1, 1));
            assertTrue(store.all("world").isEmpty());
        }
    }

    @Test
    void worldsAreIndependent() {
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            store.save("world", sample(1, 1));
            assertFalse(store.exists("world_nether", 1, 1));
            assertTrue(store.all("world_nether").isEmpty());
        }
    }

    @Test
    void worldNamesAreSanitizedInFileNames() {
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            store.save("../évil world/x", sample(0, 0));
        }
        assertTrue(Files.exists(folder.resolve("chunkpath-data")));
        try (var files = Files.list(folder.resolve("chunkpath-data"))) {
            List<String> names = files.map(p -> p.getFileName().toString()).collect(Collectors.toList());
            assertEquals(1, names.size());
            assertTrue(names.get(0).matches("[a-zA-Z0-9_\\-]+\\.db"), names.get(0));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void customAndBlankFolderNames() {
        try (ChunkGateStore store = new ChunkGateStore(folder, "autre")) {
            store.save("w", sample(0, 0));
        }
        assertTrue(Files.exists(folder.resolve("autre").resolve("w.db")));
        try (ChunkGateStore store = new ChunkGateStore(folder, "  ")) {
            store.save("w", sample(0, 0));
        }
        assertTrue(Files.exists(folder.resolve(ChunkGateStore.DEFAULT_DATABASE_FOLDER).resolve("w.db")));
    }

    @Test
    void databaseFileExistsAfterTheFirstSave() {
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            store.save("w", sample(0, 0));
            assertTrue(Files.exists(folder.resolve("chunkpath-data").resolve("w.db")));
        }
    }

    @Test
    void manyChunksForceTheIndexToGrowWithoutLosingData() {
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            for (int i = 0; i < 400; i++) store.save("world", sample(i % 40, i / 40));
            assertEquals(400, store.all("world").size());
            assertSame(sample(0, 0), store.load("world", 0, 0));
            assertSame(sample(39, 9), store.load("world", 39, 9));
            assertSame(sample(17, 4), store.load("world", 17, 4));
        }
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            assertEquals(400, store.all("world").size());
            assertSame(sample(5, 5), store.load("world", 5, 5));
        }
    }

    @Test
    void manyReplacementsAreCompactedAwayOnGrowth() throws IOException {
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            for (int round = 0; round < 3; round++) {
                for (int i = 0; i < 300; i++) {
                    ChunkGateData data = sample(i % 30, i / 30);
                    data.gates().get(0).internalDistances.put(1, 35 + round);
                    store.save("world", data);
                }
            }
            assertEquals(300, store.all("world").size());
            assertEquals(37, store.load("world", 1, 1).byId(0).internalDistances.get(1));
        }
    }

    @Test
    void deletedRecordsStayDeletedAfterReopening() {
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            store.save("world", sample(1, 1));
            store.save("world", sample(2, 2));
            store.delete("world", 1, 1);
        }
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            assertFalse(store.exists("world", 1, 1));
            assertTrue(store.exists("world", 2, 2));
        }
    }

    @Test
    void invalidMagicIsRejected() throws IOException {
        Path dir = Files.createDirectories(folder.resolve("chunkpath-data"));
        Files.write(dir.resolve("bad.db"), new byte[64]);
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            assertThrows(RuntimeException.class, () -> store.exists("bad", 0, 0));
        }
    }

    @Test
    void unsupportedFutureVersionIsRejected() throws IOException {
        Path dir = Files.createDirectories(folder.resolve("chunkpath-data"));
        try (RandomAccessFile raf = new RandomAccessFile(dir.resolve("future.db").toFile(), "rw")) {
            raf.writeInt(0x43504442);
            raf.writeInt(99);
            raf.writeInt(256);
            raf.writeInt(0);
        }
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            assertThrows(RuntimeException.class, () -> store.exists("future", 0, 0));
        }
    }

    @Test
    void legacyVersionOneIsBackedUpThenStartsEmpty() throws IOException {
        Path dir = Files.createDirectories(folder.resolve("chunkpath-data"));
        try (RandomAccessFile raf = new RandomAccessFile(dir.resolve("old.db").toFile(), "rw")) {
            raf.writeInt(0x43504442);
            raf.writeInt(1);
            raf.writeInt(256);
            raf.writeInt(0);
            raf.setLength(16 + 256 * 21);
        }
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            assertFalse(store.exists("old", 0, 0));
            assertTrue(Files.exists(dir.resolve("old.db.v1.bak")));
            store.save("old", sample(0, 0));
            assertTrue(store.exists("old", 0, 0));
        }
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            assertTrue(store.exists("old", 0, 0));
        }
    }

    @Test
    void versionTwoAndThreeAreBackedUpThenStartEmpty() throws IOException {
        Path dir = Files.createDirectories(folder.resolve("chunkpath-data"));
        try (RandomAccessFile raf = new RandomAccessFile(dir.resolve("v2.db").toFile(), "rw")) {
            raf.writeInt(0x43504442);
            raf.writeInt(2);
            raf.writeInt(256);
            raf.writeInt(0);
            raf.setLength(16 + 256 * 21);
        }
        try (RandomAccessFile raf = new RandomAccessFile(dir.resolve("v3.db").toFile(), "rw")) {
            raf.writeInt(0x43504442);
            raf.writeInt(3);
            raf.writeInt(256);
            raf.writeInt(0);
            raf.setLength(16 + 256 * 21);
        }
        try (ChunkGateStore store = new ChunkGateStore(folder)) {
            assertFalse(store.exists("v2", 0, 0));
            assertTrue(Files.exists(dir.resolve("v2.db.v2.bak")));
            assertFalse(store.exists("v3", 0, 0));
            assertTrue(Files.exists(dir.resolve("v3.db.v3.bak")));
        }
    }
}
