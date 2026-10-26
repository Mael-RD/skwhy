package skwhy.chunkpath.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import skwhy.chunkpath.Direction;
import skwhy.chunkpath.block.PathCostRules;
import skwhy.chunkpath.nbt.NbtReader;
import skwhy.chunkpath.nbt.NbtTag;
import skwhy.chunkpath.testsupport.TestChunks;

class ChunkNavMeshTest {

    @BeforeAll
    static void server() {
        TestChunks.ensureServer();
    }

    private static ChunkNavMesh mesh(NbtTag chunk) {
        return new ChunkNavMesh(chunk, TestChunks.uniformRules(5));
    }

    @Test
    void flatChunkHasOneNodePerColumnAtYOne() {
        ChunkNavMesh mesh = mesh(TestChunks.flatChunk());
        assertEquals(256, mesh.nodeCount());
        for (int i = 0; i < mesh.nodeCount(); i++) {
            assertEquals(1, mesh.y(i));
            assertEquals(5, mesh.cost(i));
        }
        assertNotEquals(-1, mesh.nodeAt(0, 1, 0));
        assertNotEquals(-1, mesh.nodeAt(15, 1, 15));
        assertEquals(-1, mesh.nodeAt(0, 0, 0)); // le sol lui-meme
        assertEquals(-1, mesh.nodeAt(0, 2, 0)); // l'air au-dessus d'un noeud n'est pas un sol
        assertEquals(-1, mesh.nodeAt(16, 1, 0));
        assertEquals(-1, mesh.nodeAt(-1, 1, 0));
        assertEquals(-1, mesh.nodeAt(0, 1, 16));
        assertEquals(-1, mesh.nodeAt(0, 99, 0));
    }

    @Test
    void boundaryNodesOfEachEdge() {
        ChunkNavMesh mesh = mesh(TestChunks.flatChunk());
        for (Direction dir : Direction.values()) {
            List<Integer> edge = mesh.boundaryNodes(dir);
            assertEquals(16, edge.size(), dir.name());
            for (int node : edge) {
                switch (dir) {
                    case WEST -> assertEquals(0, mesh.x(node));
                    case EAST -> assertEquals(15, mesh.x(node));
                    case NORTH -> assertEquals(0, mesh.z(node));
                    case SOUTH -> assertEquals(15, mesh.z(node));
                }
            }
        }
    }

    @Test
    void dijkstraCostsFivePerStepAcrossTheChunk() {
        ChunkNavMesh mesh = mesh(TestChunks.flatChunk());
        int start = mesh.nodeAt(0, 1, 0);
        int[] dist = mesh.dijkstraFrom(start, Integer.MAX_VALUE);
        assertEquals(0, dist[start]);
        assertEquals(75, dist[mesh.nodeAt(15, 1, 0)]);
        assertEquals(75, dist[mesh.nodeAt(0, 1, 15)]);
        // pas de diagonale : distance de Manhattan
        assertEquals(150, dist[mesh.nodeAt(15, 1, 15)]);
    }

    @Test
    void dijkstraCutoffLeavesFarNodesUnreached() {
        ChunkNavMesh mesh = mesh(TestChunks.flatChunk());
        int start = mesh.nodeAt(0, 1, 0);
        int[] dist = mesh.dijkstraFrom(start, 20);
        assertEquals(20, dist[mesh.nodeAt(4, 1, 0)]);
        assertEquals(Integer.MAX_VALUE, dist[mesh.nodeAt(5, 1, 0)]);
    }

    @Test
    void dijkstraMultiSourceStartsEachSourceAtZero() {
        ChunkNavMesh mesh = mesh(TestChunks.flatChunk());
        int a = mesh.nodeAt(0, 1, 0), b = mesh.nodeAt(15, 1, 0);
        int[] dist = mesh.dijkstraMultiSource(new int[] {a, b}, Integer.MAX_VALUE);
        assertEquals(0, dist[a]);
        assertEquals(0, dist[b]);
        assertEquals(35, dist[mesh.nodeAt(7, 1, 0)]); // 7 pas depuis a, 8 depuis b
    }

    @Test
    void aStoneColumnTwoBlocksHighBlocksTheCell() {
        ChunkNavMesh mesh = mesh(TestChunks.chunk(b -> b.fill(5, 1, 5, 5, 3, 5, "minecraft:stone")));
        assertEquals(-1, mesh.nodeAt(5, 1, 5));
        // la cellule du dessus de la colonne devient un noeud (sol en y=3) : le total ne change pas
        assertNotEquals(-1, mesh.nodeAt(5, 4, 5));
        assertEquals(256, mesh.nodeCount());
    }

    @Test
    void oneBlockStepIsWalkableButTwoIsNot() {
        // marche d'un bloc en x=8 : (8,1,z) est du sol, on marche a y=2 dessus
        ChunkNavMesh stepped = mesh(TestChunks.chunk(b -> b.fill(8, 1, 0, 15, 1, 15, "minecraft:stone")));
        int low = stepped.nodeAt(7, 1, 4), high = stepped.nodeAt(8, 2, 4);
        assertNotEquals(-1, low);
        assertNotEquals(-1, high);
        assertEquals(5, stepped.dijkstraFrom(low, Integer.MAX_VALUE)[high]);
        assertEquals(5, stepped.dijkstraFrom(high, Integer.MAX_VALUE)[low]);

        ChunkNavMesh wall = mesh(TestChunks.chunk(b -> b.fill(8, 1, 0, 15, 2, 15, "minecraft:stone")));
        int a = wall.nodeAt(7, 1, 4), c = wall.nodeAt(8, 3, 4);
        assertNotEquals(-1, c);
        assertEquals(Integer.MAX_VALUE, wall.dijkstraFrom(a, Integer.MAX_VALUE)[c]);
    }

    @Test
    void aFullWallSplitsTheChunkInTwoComponents() {
        ChunkNavMesh mesh = mesh(TestChunks.chunk(b -> b.fill(8, 1, 0, 8, 3, 15, "minecraft:stone")));
        int[] comp = mesh.labelComponents();
        int left = comp[mesh.nodeAt(0, 1, 0)], right = comp[mesh.nodeAt(15, 1, 0)];
        assertNotEquals(left, right);
        assertEquals(left, comp[mesh.nodeAt(7, 1, 15)]);
        assertEquals(right, comp[mesh.nodeAt(9, 1, 15)]);
    }

    @Test
    void doorsAreWalkableButFencesAndWaterAreNot() {
        ChunkNavMesh mesh = mesh(TestChunks.chunk(b -> {
            b.set(3, 1, 3, "minecraft:oak_fence");
            b.set(5, 1, 5, "minecraft:water");
            b.set(7, 1, 7, "minecraft:oak_door");
            b.set(7, 2, 7, "minecraft:oak_door");
        }));
        assertEquals(-1, mesh.nodeAt(3, 1, 3));
        assertEquals(-1, mesh.nodeAt(5, 1, 5));
        assertNotEquals(-1, mesh.nodeAt(7, 1, 7));
    }

    @Test
    void ladderIsWalkableWithoutAFloorAndLinksVertically() {
        ChunkNavMesh mesh = mesh(TestChunks.builder(0).fill(0, 0, 0, 15, 0, 15, "minecraft:stone")
                .fill(4, 1, 4, 4, 5, 4, "minecraft:ladder").fill(3, 1, 4, 3, 5, 4, "minecraft:stone").build());
        int bottom = mesh.nodeAt(4, 1, 4), top = mesh.nodeAt(4, 4, 4);
        assertNotEquals(-1, bottom);
        assertNotEquals(-1, top);
        assertNotEquals(-1, mesh.nodeAt(4, 5, 4));
        int[] dist = mesh.dijkstraFrom(bottom, Integer.MAX_VALUE);
        assertTrue(dist[top] != Integer.MAX_VALUE);
        assertEquals(dist[mesh.nodeAt(4, 2, 4)] + 5 + 5, dist[top]);
    }

    @Test
    void mixedSectionsDecodeExactly() {
        // quatre blocs differents dans une section (palette >= 5 : 4 bits par entree, decodage bit a bit)
        NbtTag chunk = TestChunks.chunk(b -> {
            b.set(1, 1, 1, "minecraft:oak_planks");
            b.set(2, 1, 1, "minecraft:dirt");
            b.set(3, 1, 1, "minecraft:glass");
            b.set(4, 1, 1, "minecraft:cobblestone");
        });
        ChunkNavMesh mesh = mesh(chunk);
        assertEquals(-1, mesh.nodeAt(1, 1, 1));
        assertEquals(-1, mesh.nodeAt(4, 1, 1));
        assertNotEquals(-1, mesh.nodeAt(1, 2, 1)); // sur le dessus d'un bloc plein
        assertEquals(256 - 4 + 4, mesh.nodeCount());
    }

    @Test
    void largePaletteUsesMoreBitsPerEntry() {
        String[] blocks = {"stone", "dirt", "cobblestone", "oak_planks", "spruce_planks", "birch_planks", "sand", "gravel",
                "granite", "diorite", "andesite", "clay", "netherrack", "end_stone", "obsidian", "bricks", "glass"};
        NbtTag chunk = TestChunks.chunk(b -> {
            for (int i = 0; i < blocks.length; i++) b.set(i % 16, 1, i / 16, "minecraft:" + blocks[i]);
        });
        ChunkNavMesh mesh = mesh(chunk);
        for (int i = 0; i < blocks.length; i++) {
            assertEquals(-1, mesh.nodeAt(i % 16, 1, i / 16), blocks[i]);
        }
    }

    @Test
    void uniformSolidSectionIsRead() {
        NbtTag chunk = TestChunks.builder(1).fill(0, 0, 0, 15, 15, 15, "minecraft:stone").build();
        ChunkNavMesh mesh = mesh(chunk);
        // section 0 pleine, section 1 vide : un sol en y=15 donc des noeuds en y=16
        assertEquals(256, mesh.nodeCount());
        assertEquals(16, mesh.y(0));
    }

    @Test
    void groundCostFollowsTheRules() {
        PathCostRules rules = new PathCostRules(List.of(new PathCostRules.CostRule(List.of(org.bukkit.Material.STONE), List.of(), 2)));
        ChunkNavMesh mesh = new ChunkNavMesh(TestChunks.flatChunk(), rules);
        assertEquals(2, mesh.cost(mesh.nodeAt(3, 1, 3)));
    }

    @Test
    void blockCoveredByNoRuleIsNotWalkable() {
        PathCostRules rules = new PathCostRules(List.of(new PathCostRules.CostRule(List.of(org.bukkit.Material.DIRT), List.of(), 2)));
        ChunkNavMesh mesh = new ChunkNavMesh(TestChunks.flatChunk(), rules);
        assertEquals(0, mesh.nodeCount()); // le sol plat est en pierre : aucune règle ne le couvre
    }

    @Test
    void chunkWithoutSectionsIsRejected() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(NbtTag.COMPOUND);
        out.writeUTF("");
        out.writeByte(NbtTag.END);
        NbtTag empty = NbtReader.readRoot(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertThrows(IllegalStateException.class, () -> mesh(empty));
    }

    @Test
    void debugColumnListsEveryLevel() {
        ChunkNavMesh mesh = mesh(TestChunks.flatChunk());
        String text = mesh.debugColumn(0, 0, 0, 3);
        assertTrue(text.contains("y=1 "));
        assertTrue(text.contains("name=minecraft:stone"));
    }

    @Test
    void trapdoorsOnTheFloorOrOpenDoNotBlockAndCostNothing() {
        for (String state : new String[] {"half=bottom,open=false", "half=top,open=false", "half=bottom,open=true", "half=top,open=true"}) {
            NbtTag chunk = TestChunks.builder(0).fill(0, 0, 0, 15, 0, 15, "minecraft:stone")
                    .set(5, 1, 5, "minecraft:oak_trapdoor[" + state + "]").build();
            ChunkNavMesh mesh = mesh(chunk);
            int node = mesh.nodeAt(5, 1, 5);
            assertNotEquals(-1, node, state);
            assertEquals(5, mesh.cost(node), state); // le sol dessous, pas la trappe
        }
    }

    @Test
    void closedTopTrapdoorOverAHoleIsAFloorWithItsOwnCost() {
        PathCostRules rules = new PathCostRules(java.util.List.of(
                new PathCostRules.CostRule(java.util.List.of(org.bukkit.Material.OAK_TRAPDOOR), java.util.List.of(), 9),
                new PathCostRules.CostRule(java.util.List.of(), java.util.List.of(), 5)));
        NbtTag chunk = TestChunks.builder(0).fill(0, 0, 0, 15, 0, 15, "minecraft:stone")
                .air(5, 0, 5, 5, 0, 5)
                .set(5, 0, 5, "minecraft:oak_trapdoor[half=top,open=false]").build();
        ChunkNavMesh mesh = new ChunkNavMesh(chunk, rules);
        int above = mesh.nodeAt(5, 1, 5);
        assertNotEquals(-1, above);
        assertEquals(9, mesh.cost(above));
    }

    @Test
    void openOrBottomTrapdoorOverAHoleIsNotAFloor() {
        for (String state : new String[] {"half=bottom,open=false", "half=top,open=true", "half=bottom,open=true"}) {
            NbtTag chunk = TestChunks.builder(0).fill(0, 0, 0, 15, 0, 15, "minecraft:stone")
                    .set(5, 0, 5, "minecraft:oak_trapdoor[" + state + "]").build();
            assertEquals(-1, mesh(chunk).nodeAt(5, 1, 5), state);
        }
    }
}
