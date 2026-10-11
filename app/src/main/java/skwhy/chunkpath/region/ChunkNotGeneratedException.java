package skwhy.chunkpath.region;

/**
 * Levée quand le chunk demandé n'existe pas dans le fichier région (jamais généré),
 * ou que son statut de génération n'est pas "full". Le système d'analyse chunk-à-chunk
 * ne doit jamais essayer de générer un chunk lui-même : c'est une erreur d'appel.
 */
public class ChunkNotGeneratedException extends RuntimeException {
    public final int chunkX;
    public final int chunkZ;

    public ChunkNotGeneratedException(int chunkX, int chunkZ, String reason) {
        super("Chunk (" + chunkX + ", " + chunkZ + ") non disponible: " + reason);
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }
}
