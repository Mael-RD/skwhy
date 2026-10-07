package skwhy.chunkpath.region;

/**
 * Levée quand le fichier région existe mais ne peut pas être lu correctement : fichier
 * vide ou tronqué, en-tête ou longueur de chunk incohérents, compression non supportée,
 * NBT illisible. Peut arriver si le serveur est en train d'écrire le fichier au même
 * moment : aucune nouvelle tentative n'est faite ici, c'est à l'appelant de décider.
 */
public class RegionReadException extends RuntimeException {
    public final int chunkX;
    public final int chunkZ;

    public RegionReadException(int chunkX, int chunkZ, String reason) {
        this(chunkX, chunkZ, reason, null);
    }

    public RegionReadException(int chunkX, int chunkZ, String reason, Throwable cause) {
        super("Chunk (" + chunkX + ", " + chunkZ + ") illisible: " + reason, cause);
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }
}
