package skwhy.chunkpath;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DirectionTest {

    @Test
    void oppositeIsAnInvolution() {
        for (Direction d : Direction.values()) {
            assertEquals(d, d.opposite().opposite());
            assertEquals(-d.dx, d.opposite().dx);
            assertEquals(-d.dz, d.opposite().dz);
        }
    }

    @Test
    void specificOpposites() {
        assertEquals(Direction.SOUTH, Direction.NORTH.opposite());
        assertEquals(Direction.EAST, Direction.WEST.opposite());
    }

    @Test
    void offsetsMatchMinecraftAxes() {
        assertEquals(-1, Direction.NORTH.dz);
        assertEquals(1, Direction.SOUTH.dz);
        assertEquals(-1, Direction.WEST.dx);
        assertEquals(1, Direction.EAST.dx);
    }
}
