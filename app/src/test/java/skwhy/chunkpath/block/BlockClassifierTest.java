package skwhy.chunkpath.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import skwhy.chunkpath.testsupport.TestChunks;

class BlockClassifierTest {

    @BeforeAll
    static void server() {
        TestChunks.ensureServer();
    }

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:short_grass", "minecraft:poppy",
            "minecraft:torch", "minecraft:white_carpet", "minecraft:moss_carpet", "minecraft:oak_trapdoor", "minecraft:iron_trapdoor", "minecraft:oak_door", "minecraft:iron_door"})
    void occupiableButNotStandable(String name) {
        byte flags = BlockClassifier.classify(name);
        assertTrue(BlockClassifier.isOccupiable(flags), name);
        assertFalse(BlockClassifier.isStandable(flags), name);
        assertFalse(BlockClassifier.isClimbable(flags), name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:stone", "minecraft:dirt", "minecraft:oak_planks", "minecraft:oak_stairs", "minecraft:stone_slab",
            "minecraft:bedrock", "minecraft:glass", "minecraft:ice"})
    void solidBlocksAreStandable(String name) {
        byte flags = BlockClassifier.classify(name);
        assertTrue(BlockClassifier.isStandable(flags), name);
        assertFalse(BlockClassifier.isOccupiable(flags), name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:ladder", "minecraft:vine", "minecraft:scaffolding", "minecraft:weeping_vines"})
    void climbableBlocksAreOccupiableAndClimbable(String name) {
        byte flags = BlockClassifier.classify(name);
        assertTrue(BlockClassifier.isClimbable(flags), name);
        assertTrue(BlockClassifier.isOccupiable(flags), name);
        assertFalse(BlockClassifier.isStandable(flags), name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:water", "minecraft:lava", "minecraft:oak_fence",
            "minecraft:nether_brick_fence", "minecraft:cobblestone_wall", "minecraft:oak_fence_gate"})
    void blockedBlocksAreNeitherOccupiableNorStandable(String name) {
        assertEquals(0, BlockClassifier.classify(name), name);
    }

    @Test
    void unknownBlockIsTreatedAsSolidGround() {
        byte flags = BlockClassifier.classify("modded:mystery_block");
        assertTrue(BlockClassifier.isStandable(flags));
        assertFalse(BlockClassifier.isOccupiable(flags));
    }

    @Test
    void resolveMaterial() {
        assertEquals(Material.STONE, BlockClassifier.resolveMaterial("minecraft:stone"));
        assertEquals(Material.OAK_PLANKS, BlockClassifier.resolveMaterial("minecraft:oak_planks"));
        assertNull(BlockClassifier.resolveMaterial("modded:mystery_block"));
        assertNotNull(BlockClassifier.noCollisionBlocks());
    }

    @Test
    void flagPredicates() {
        assertTrue(BlockClassifier.isOccupiable(BlockClassifier.OCCUPIABLE));
        assertTrue(BlockClassifier.isStandable(BlockClassifier.STANDABLE));
        assertTrue(BlockClassifier.isClimbable((byte) (BlockClassifier.OCCUPIABLE | BlockClassifier.CLIMBABLE)));
        assertFalse(BlockClassifier.isStandable(BlockClassifier.OCCUPIABLE));
        assertFalse(BlockClassifier.isOccupiable((byte) 0));
    }

    @Test
    void trapdoorsAreCrossableWhateverTheirState() {
        for (String half : new String[] {"bottom", "top"}) {
            for (String open : new String[] {"true", "false"}) {
                byte flags = BlockClassifier.classify("minecraft:oak_trapdoor", half, open);
                assertTrue(BlockClassifier.isOccupiable(flags), half + " " + open);
            }
        }
    }

    @Test
    void onlyAClosedTopTrapdoorIsAlsoAFloor() {
        assertTrue(BlockClassifier.isStandable(BlockClassifier.classify("minecraft:oak_trapdoor", "top", "false")));
        assertFalse(BlockClassifier.isStandable(BlockClassifier.classify("minecraft:oak_trapdoor", "top", "true")));
        assertFalse(BlockClassifier.isStandable(BlockClassifier.classify("minecraft:oak_trapdoor", "bottom", "false")));
        assertFalse(BlockClassifier.isStandable(BlockClassifier.classify("minecraft:oak_trapdoor", "bottom", "true")));
        assertFalse(BlockClassifier.isStandable(BlockClassifier.classify("minecraft:oak_trapdoor", null, null)));
    }

    @Test
    void stateIsIgnoredForOtherBlocks() {
        assertEquals(BlockClassifier.classify("minecraft:stone"), BlockClassifier.classify("minecraft:stone", "top", "false"));
    }
}
