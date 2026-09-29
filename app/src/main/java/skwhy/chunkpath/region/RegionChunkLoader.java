package skwhy.chunkpath.region;

import skwhy.chunkpath.nbt.NbtReader;
import skwhy.chunkpath.nbt.NbtTag;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Charge le tag racine NBT d'un chunk directement depuis le fichier région (.mca),
 * sans jamais passer par le pipeline de chargement de Bukkit/Paper.
 *
 * Ne gère QUE les chunks déjà générés (offset != 0 dans le header ET Status == "full").
 * Tout autre cas -> ChunkNotGeneratedException, jamais de génération à la volée.
 *
 * Format Anvil ciblé : chunks post-1.18 (pas de tag "Level", sections indexées de -4 à 19
 * typiquement, palette de blocs dans section.block_states.palette / .data).
 */
public final class RegionChunkLoader {

    private static final int SECTOR_SIZE = 4096;

    private RegionChunkLoader() {
    }

    /**
     * @param regionFolder dossier "region" (ou "DIM-1/region", "DIM1/region") du monde concerné
     * @param chunkX       coordonnée chunk absolue (pas coordonnée bloc)
     * @param chunkZ       coordonnée chunk absolue
     */
    public static NbtTag loadChunkRoot(Path regionFolder, int chunkX, int chunkZ) {
        int regionX = chunkX >> 5;
        int regionZ = chunkZ >> 5;
        Path regionFile = regionFolder.resolve("r." + regionX + "." + regionZ + ".mca");

        if (!Files.exists(regionFile)) {
            throw new ChunkNotGeneratedException(chunkX, chunkZ, "fichier région introuvable: " + regionFile);
        }

        int localX = chunkX & 31;
        int localZ = chunkZ & 31;
        int headerIndex = localX + localZ * 32;

        try (RandomAccessFile raf = new RandomAccessFile(regionFile.toFile(), "r")) {
            raf.seek(headerIndex * 4L);
            int entry = raf.readInt(); // 3 octets offset (secteurs) + 1 octet sectorCount
            int sectorOffset = entry >>> 8;
            int sectorCount = entry & 0xFF;

            if (sectorOffset == 0 && sectorCount == 0) {
                throw new ChunkNotGeneratedException(chunkX, chunkZ, "chunk absent du fichier région (jamais généré)");
            }

            raf.seek(sectorOffset * (long) SECTOR_SIZE);
            int length = raf.readInt(); // inclut l'octet de type de compression
            byte compressionTypeByte = raf.readByte();

            if ((compressionTypeByte & 0x80) != 0) {
                // Bit "stocké en externe" (.mcc) : cas rarissime pour du terrain normal.
                throw new IOException("Chunk stocké dans un fichier .mcc externe (non supporté par ce loader): "
                        + chunkX + "," + chunkZ);
            }

            byte[] payload = new byte[length - 1];
            raf.readFully(payload);

            NbtTag root;
            try (DataInputStream nbtIn = new DataInputStream(decompress(compressionTypeByte, payload))) {
                root = NbtReader.readRoot(nbtIn);
            }

            String status = root.getString("Status", root.getString("status", null));
            if (status == null || !(status.equals("full") || status.equals("minecraft:full"))) {
                throw new ChunkNotGeneratedException(chunkX, chunkZ,
                        "chunk présent mais génération incomplète (status=" + status + ")");
            }

            return root;
        } catch (ChunkNotGeneratedException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("Erreur de lecture du chunk (" + chunkX + "," + chunkZ + ")", e);
        }
    }

    private static java.io.InputStream decompress(byte compressionType, byte[] payload) throws IOException {
        ByteArrayInputStream raw = new ByteArrayInputStream(payload);
        return switch (compressionType) {
            case 1 -> new GZIPInputStream(raw);
            case 2 -> new InflaterInputStream(raw); // zlib (RFC1950), défaut vanilla
            case 3 -> raw; // non compressé
            default -> throw new IOException("Type de compression non supporté: " + compressionType);
        };
    }
}
