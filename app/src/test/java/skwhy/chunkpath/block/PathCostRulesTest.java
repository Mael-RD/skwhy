package skwhy.chunkpath.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import skwhy.chunkpath.block.PathCostRules.CostRule;
import skwhy.chunkpath.testsupport.TestChunks;

class PathCostRulesTest {

    @BeforeAll
    static void server() {
        TestChunks.ensureServer();
    }

    @Test
    void singleCatchAllRuleCostsEverything() {
        PathCostRules rules = TestChunks.uniformRules(5);
        assertEquals(5, rules.defaultCost());
        assertEquals(5, rules.costOf("minecraft:stone"));
        assertEquals(5, rules.costOf("modded:unknown"));
    }

    @Test
    void materialRuleMatches() {
        PathCostRules rules = new PathCostRules(List.of(
                new CostRule(List.of(Material.STONE_BRICKS, Material.DIRT_PATH), List.of(), 1),
                new CostRule(List.of(), List.of(), 5)));
        assertEquals(1, rules.costOf("minecraft:stone_bricks"));
        assertEquals(1, rules.costOf("minecraft:dirt_path"));
        assertEquals(5, rules.costOf("minecraft:stone"));
    }

    @Test
    void tagRuleMatches() {
        PathCostRules rules = new PathCostRules(List.of(
                new CostRule(List.of(), List.of(Tag.LEAVES), 8),
                new CostRule(List.of(), List.of(), 5)));
        assertEquals(8, rules.costOf("minecraft:oak_leaves"));
        assertEquals(8, rules.costOf("minecraft:birch_leaves"));
        assertEquals(5, rules.costOf("minecraft:oak_log"));
    }

    @Test
    void firstMatchingRuleWins() {
        PathCostRules rules = new PathCostRules(List.of(
                new CostRule(List.of(Material.OAK_LEAVES), List.of(), 2),
                new CostRule(List.of(), List.of(Tag.LEAVES), 8)));
        assertEquals(2, rules.costOf("minecraft:oak_leaves"));
        assertEquals(8, rules.costOf("minecraft:birch_leaves"));
    }

    @Test
    void withoutDefaultRuleUncoveredBlocksAreBlocked() {
        PathCostRules rules = new PathCostRules(List.of(new CostRule(List.of(Material.STONE), List.of(), 1)));
        assertEquals(PathCostRules.BLOCKED, rules.defaultCost());
        assertEquals(PathCostRules.BLOCKED, rules.costOf("minecraft:dirt"));
        assertEquals(1, rules.costOf("minecraft:stone"));
    }

    @Test
    void ruleWithoutBlocksIsTheDefaultRuleWhateverItsPosition() {
        PathCostRules rules = new PathCostRules(List.of(
                new CostRule(List.of(), List.of(), 0),
                new CostRule(List.of(Material.STONE), List.of(), 3)));
        assertEquals(0, rules.defaultCost());
        assertEquals(0, rules.costOf("minecraft:dirt"));
        assertEquals(3, rules.costOf("minecraft:stone"));
    }

    @Test
    void customDefaultAndUnknownBlocks() {
        PathCostRules rules = new PathCostRules(List.of(
                new CostRule(List.of(Material.STONE), List.of(), 1),
                new CostRule(List.of(), List.of(), 7)));
        assertEquals(7, rules.defaultCost());
        assertEquals(7, rules.costOf("modded:unknown"));
        assertEquals(1, rules.costOf("minecraft:stone"));
        // deuxieme appel : valeur en cache identique
        assertEquals(1, rules.costOf("minecraft:stone"));
    }
}
