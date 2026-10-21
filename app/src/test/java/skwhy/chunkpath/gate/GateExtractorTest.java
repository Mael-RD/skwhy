package skwhy.chunkpath.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import skwhy.chunkpath.Direction;
import skwhy.chunkpath.block.PathCostRules;
import skwhy.chunkpath.graph.ChunkNavMesh;
import skwhy.chunkpath.nbt.NbtTag;
import skwhy.chunkpath.testsupport.TestChunks;

class GateExtractorTest {

    @BeforeAll
    static void server() {
        TestChunks.ensureServer();
    }

    private static List<Gate> extract(NbtTag chunk, int cx, int cz) {
        return GateExtractor.extract(new ChunkNavMesh(chunk, PathCostRules.defaults()), cx, cz, GateExtractor.DEFAULT_MERGE_THRESHOLD);
    }

    private static List<Gate> onEdge(List<Gate> gates, Direction dir) {
        return gates.stream().filter(g -> g.edge == dir).collect(Collectors.toList());
    }

    @Test
    void flatChunkHasOneGatePerEdge() {
        List<Gate> gates = extract(TestChunks.flatChunk(), 0, 0);
        assertEquals(4, gates.size());
        for (Direction d : Direction.values()) {
            assertEquals(1, onEdge(gates, d).size(), d.name());
            assertEquals(16, onEdge(gates, d).get(0).memberBlocksWorld.size());
        }
    }

    @Test
    void gateIdsAreUniqueWithinTheChunk() {
        List<Gate> gates = extract(TestChunks.flatChunk(), 0, 0);
        assertEquals(4, gates.stream().map(g -> g.id).collect(Collectors.toSet()).size());
        for (Gate g : gates) {
            assertEquals(0, g.chunkX);
            assertEquals(0, g.chunkZ);
            assertEquals("0,0," + g.id, g.globalId());
        }
    }

    @Test
    void memberBlocksAreInWorldCoordinates() {
        List<Gate> gates = extract(TestChunks.flatChunk(), 2, -3);
        Gate east = onEdge(gates, Direction.EAST).get(0);
        for (int[] block : east.memberBlocksWorld) {
            assertEquals(2 * 16 + 15, block[0]);
            assertEquals(1, block[1]);
            assertTrue(block[2] >= -3 * 16 && block[2] < -3 * 16 + 16);
        }
        Gate north = onEdge(gates, Direction.NORTH).get(0);
        for (int[] block : north.memberBlocksWorld) assertEquals(-3 * 16, block[2]);
    }

    @Test
    void representativeIsTheMemberClosestToTheCentre() {
        List<Gate> gates = extract(TestChunks.flatChunk(), 0, 0);
        Gate east = onEdge(gates, Direction.EAST).get(0);
        // 16 blocs, z 0..15 : le centre est 7.5, le premier des plus proches rencontre est z=7
        assertEquals(15, east.representativeWorld[0]);
        assertTrue(east.representativeWorld[2] == 7 || east.representativeWorld[2] == 8);
    }

    @Test
    void distancesBetweenGatesOfTheSameChunk() {
        List<Gate> gates = extract(TestChunks.flatChunk(), 0, 0);
        Gate north = onEdge(gates, Direction.NORTH).get(0), south = onEdge(gates, Direction.SOUTH).get(0);
        Gate west = onEdge(gates, Direction.WEST).get(0);
        assertEquals(75, north.internalDistances.get(south.id));
        assertEquals(75, south.internalDistances.get(north.id));
        // le coin (0,1,0) appartient a la fois a NORTH et WEST
        assertEquals(0, north.internalDistances.get(west.id));
    }

    @Test
    void openChunkGatesAreNotDeadEnds() {
        for (Gate g : extract(TestChunks.flatChunk(), 0, 0)) assertFalse(g.deadEndCandidate, g.edge.name());
    }

    @Test
    void aFullWallSplitsTheParallelEdgesAndItsTopIsAThirdGate() {
        List<Gate> gates = extract(TestChunks.chunk(b -> b.fill(8, 1, 0, 8, 3, 15, "minecraft:stone")), 0, 0);
        // gauche, droite, et le dessus du mur (y=4) que l'on ne peut pas atteindre depuis le sol (3 blocs)
        assertEquals(3, onEdge(gates, Direction.NORTH).size());
        assertEquals(3, onEdge(gates, Direction.SOUTH).size());
        assertEquals(1, onEdge(gates, Direction.WEST).size());
        assertEquals(1, onEdge(gates, Direction.EAST).size());
        Gate west = onEdge(gates, Direction.WEST).get(0), east = onEdge(gates, Direction.EAST).get(0);
        assertFalse(west.internalDistances.containsKey(east.id)); // le mur les separe
        assertFalse(east.internalDistances.containsKey(west.id));
    }

    @Test
    void mergeThresholdIsStrict() {
        // un pilier sur le bord nord : le contournement coute 4 pas = 20, donc PAS fusionne sous le seuil 20
        NbtTag chunk = TestChunks.chunk(b -> b.fill(4, 1, 0, 4, 3, 0, "minecraft:stone"));
        ChunkNavMesh mesh = new ChunkNavMesh(chunk, PathCostRules.defaults());
        // deux cotes du pilier + le dessus du pilier (noeud isole en y=4)
        assertEquals(3, onEdge(GateExtractor.extract(mesh, 0, 0, 20), Direction.NORTH).size());
        assertEquals(2, onEdge(GateExtractor.extract(mesh, 0, 0, 21), Direction.NORTH).size());
    }

    @Test
    void lowMergeThresholdSplitsAnEdge() {
        ChunkNavMesh mesh = new ChunkNavMesh(TestChunks.flatChunk(), PathCostRules.defaults());
        // seuil 6 : deux noeuds voisins (cout 5) fusionnent encore, mais le chain complet 15 pas aussi (transitif)
        List<Gate> merged = GateExtractor.extract(mesh, 0, 0, 6);
        assertEquals(4, merged.size());
        // seuil 5 : plus rien ne fusionne (cout 5 n'est pas < 5) : une porte par bloc de bord
        List<Gate> single = GateExtractor.extract(mesh, 0, 0, 5);
        assertEquals(onEdge(single, Direction.NORTH).size(), 16);
    }

    @Test
    void pocketOnTheEdgeIsADeadEndCandidate() {
        // case (0,1,5) emmuree par 3 blocs de haut sauf vers l'exterieur du chunk
        NbtTag chunk = TestChunks.chunk(b -> {
            b.fill(1, 1, 5, 1, 3, 5, "minecraft:stone");
            b.fill(0, 1, 4, 0, 3, 4, "minecraft:stone");
            b.fill(0, 1, 6, 0, 3, 6, "minecraft:stone");
        });
        List<Gate> gates = extract(chunk, 0, 0);
        List<Gate> west = onEdge(gates, Direction.WEST);
        Gate pocket = west.stream().filter(g -> g.memberBlocksWorld.size() == 1 && g.memberBlocksWorld.get(0)[2] == 5).findFirst().orElse(null);
        assertNotNull(pocket);
        assertTrue(pocket.deadEndCandidate);
        assertTrue(pocket.internalDistances.isEmpty());
    }

    @Test
    void extractedChunksLinkEndToEnd() {
        List<Gate> left = extract(TestChunks.flatChunk(), 0, 0);
        List<Gate> right = extract(TestChunks.flatChunk(), 1, 0);
        ChunkGateData a = new ChunkGateData(0, 0, left), b = new ChunkGateData(1, 0, right);
        ChunkGateLinker.link(a, b, Direction.EAST);
        Gate ge = a.onEdge(Direction.EAST).get(0), gw = b.onEdge(Direction.WEST).get(0);
        assertEquals(Set.of(gw.globalId()), ge.neighborLinks);
        assertEquals(Set.of(ge.globalId()), gw.neighborLinks);
        assertEquals(4, a.gates().size());
        assertEquals(4, b.gates().size());
    }

    @Test
    void deadEndPocketNextToAnOpenChunkIsPrunedAfterLinking() {
        NbtTag pocketChunk = TestChunks.chunk(b -> {
            b.fill(1, 1, 5, 1, 3, 5, "minecraft:stone");
            b.fill(0, 1, 4, 0, 3, 4, "minecraft:stone");
            b.fill(0, 1, 6, 0, 3, 6, "minecraft:stone");
        });
        ChunkGateData a = new ChunkGateData(0, 0, extract(pocketChunk, 0, 0));
        ChunkGateData b = new ChunkGateData(-1, 0, extract(TestChunks.flatChunk(), -1, 0));
        Gate pocket = a.onEdge(Direction.WEST).stream()
                .filter(g -> g.memberBlocksWorld.size() == 1 && g.memberBlocksWorld.get(0)[2] == 5).findFirst().orElseThrow();
        ChunkGateLinker.link(a, b, Direction.WEST);
        // le cul-de-sac (un seul voisin) disparait, la grande porte ouest reste
        assertEquals(null, a.byId(pocket.id));
        assertTrue(a.onEdge(Direction.WEST).stream().anyMatch(g -> g.memberBlocksWorld.size() > 1));
    }
}
