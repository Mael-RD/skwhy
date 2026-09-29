package skwhy.chunkpath;

/** Les 4 bords d'un chunk (16x16), utilisés pour repérer les portes et leurs voisins. */
public enum Direction {
    NORTH(0, -1), // z = 0, voisin = chunkZ - 1
    SOUTH(0, 1),  // z = 15, voisin = chunkZ + 1
    WEST(-1, 0),  // x = 0, voisin = chunkX - 1
    EAST(1, 0);   // x = 15, voisin = chunkX + 1

    public final int dx;
    public final int dz;

    Direction(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public Direction opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }
}
