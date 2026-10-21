package skwhy.chunkpath.move;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class MthTest {

    @Test
    void wrapDegreesStaysInHalfOpenRange() {
        assertEquals(0f, Mth.wrapDegrees(0f));
        assertEquals(90f, Mth.wrapDegrees(90f));
        assertEquals(-180f, Mth.wrapDegrees(180f));
        assertEquals(-170f, Mth.wrapDegrees(190f));
        assertEquals(170f, Mth.wrapDegrees(-190f));
        assertEquals(0f, Mth.wrapDegrees(360f));
        assertEquals(10f, Mth.wrapDegrees(730f));
        assertEquals(-180f, Mth.wrapDegrees(-180f));
    }

    @Test
    void degreesDifferenceTakesTheShortestWay() {
        assertEquals(20f, Mth.degreesDifference(350f, 10f));
        assertEquals(-20f, Mth.degreesDifference(10f, 350f));
        assertEquals(0f, Mth.degreesDifference(45f, 45f));
        assertEquals(90f, Mth.degreesDifference(0f, 90f));
    }

    @Test
    void rotateIfNecessaryLimitsTheStep() {
        // la base est en retard de 90 degres : on ne peut corriger que de 30
        assertEquals(60f, Mth.rotateIfNecessary(0f, 90f, 30f), 1e-4f);
        // ecart (5) plus petit que la limite : retranche l'ecart complet
        assertEquals(5f, Mth.rotateIfNecessary(5f, 10f, 30f), 1e-4f);
        // sens inverse, par le chemin le plus court
        assertEquals(-60f, Mth.rotateIfNecessary(0f, -90f, 30f), 1e-4f);
    }

    @Test
    void isVectorFinite() {
        assertTrue(Mth.isVectorFinite(new Vector(1, 2, 3)));
        assertFalse(Mth.isVectorFinite(null));
        assertFalse(Mth.isVectorFinite(new Vector(Double.NaN, 0, 0)));
        assertFalse(Mth.isVectorFinite(new Vector(0, Double.POSITIVE_INFINITY, 0)));
        assertFalse(Mth.isVectorFinite(new Vector(0, 0, Double.NEGATIVE_INFINITY)));
    }

    @Test
    void cardinalDirectionFromYaw() {
        assertEquals(BlockFace.SOUTH, Mth.getCardinalDirection(0f));
        assertEquals(BlockFace.WEST, Mth.getCardinalDirection(90f));
        assertEquals(BlockFace.NORTH, Mth.getCardinalDirection(180f));
        assertEquals(BlockFace.EAST, Mth.getCardinalDirection(270f));
        assertEquals(BlockFace.EAST, Mth.getCardinalDirection(-90f));
        assertEquals(BlockFace.NORTH, Mth.getCardinalDirection(-180f));
        assertEquals(BlockFace.SOUTH, Mth.getCardinalDirection(360f));
        assertEquals(BlockFace.SOUTH, Mth.getCardinalDirection(40f));
        assertEquals(BlockFace.WEST, Mth.getCardinalDirection(50f));
    }
}
