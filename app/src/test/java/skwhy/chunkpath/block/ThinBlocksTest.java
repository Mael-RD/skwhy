package skwhy.chunkpath.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;

class ThinBlocksTest {

    private static BoundingBox box(double minY, double maxY) {
        return new BoundingBox(0, minY, 0, 1, maxY, 1);
    }

    @Test
    void carpetAndClosedTrapdoorOnTheFloorAreThin() {
        assertTrue(ThinBlocks.isThin(List.of(box(0, 0.0625))));
        assertTrue(ThinBlocks.isThin(List.of(box(0, 0.1875))));
    }

    @Test
    void thresholdIsInclusive() {
        assertTrue(ThinBlocks.isThin(List.of(box(0, 0.25)))); // snow, 3 layers
        assertFalse(ThinBlocks.isThin(List.of(box(0, 0.375)))); // daylight sensor
        assertFalse(ThinBlocks.isThin(List.of(box(0, 0.5)))); // 5 layers, bottom slab
    }

    @Test
    void trapdoorOnTheCeilingIsThin() {
        assertTrue(ThinBlocks.isThin(List.of(box(0.8125, 1.0))));
    }

    @Test
    void thinBoxFloatingInTheMiddleIsNot() {
        assertFalse(ThinBlocks.isThin(List.of(box(0.4, 0.5))));
    }

    @Test
    void tallPanelIsNotThinWhateverItsWidth() {
        // open trapdoor / fence gate: thin sideways, one block high
        assertFalse(ThinBlocks.isThin(List.of(new BoundingBox(0, 0, 0.8125, 1, 1, 1))));
    }

    @Test
    void oneThickBoxMakesTheWholeShapeThick() {
        assertFalse(ThinBlocks.isThin(List.of(box(0, 0.0625), box(0, 1.5))));
    }

    @Test
    void noCollisionIsNotAThinBlock() {
        assertFalse(ThinBlocks.isThin(List.<BoundingBox>of()));
    }

    @Test
    void carpetsAreRecognisedByName() {
        assertTrue(ThinBlocks.isCarpet(Material.WHITE_CARPET));
        assertTrue(ThinBlocks.isCarpet(Material.MOSS_CARPET));
        assertFalse(ThinBlocks.isCarpet(Material.WHITE_WOOL));
    }
}
