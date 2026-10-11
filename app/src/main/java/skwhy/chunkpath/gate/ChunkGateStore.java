package skwhy.chunkpath.gate;

import skwhy.chunkpath.Direction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stocke/relit les portes des chunks, une base de données par monde, dans
 * {@code <dossier du plugin>/<dossier des bases de données>/<nom du monde>.db}.
 *
 * Un monde = UN SEUL fichier (pas un fichier par chunk) : table d'index à adressage
 * ouvert (clé chunk -> position/longueur) suivie d'une zone de données où chaque
 * enregistrement de chunk est simplement ajouté à la suite (écriture en append, jamais
 * réécrite sur place). Ça donne une lecture en un seul accès disque (index tenu en
 * mémoire une fois le fichier ouvert) et une écriture tout aussi directe (append +
 * mise à jour d'un seul slot d'index), sans le coût de milliers de petits fichiers
 * séparés sur le système de fichiers.
 *
 * Un ré-enregistrement dont le contenu est strictement identique à la version déjà stockée
 * (même longueur, mêmes octets) est un no-op complet : rien n'est réécrit et le fichier ne
 * grossit pas. Un chunk réanalysé dont le contenu a réellement changé (typiquement : un
 * voisin ré-enregistré après liaison, cf. {@link skwhy.chunkpath.ChunkGateService}) laisse en
 * revanche l'ancienne version orpheline dans la zone de données ; ce gaspillage reste borné
 * (chaque chunk n'a que 4 voisins, donc au plus ~5 versions cumulées dans sa vie) et est de
 * toute façon entièrement purgé dès que la table d'index grossit (cf.
 * {@link WorldDatabase#growAndCompact}), ce qui réécrit le fichier en ne conservant que la
 * dernière version de chaque chunk.
 *
 * Le fichier d'un monde est créé (avec un index vide) à la toute première sauvegarde
 * d'un chunk de ce monde, pas avant.
 *
 * Thread-safety : une {@link WorldDatabase} par monde, chacune verrouillée sur sa propre
 * instance ; deux mondes différents ne se bloquent jamais entre eux.
 */
public final class ChunkGateStore implements Closeable {

    public static final String DEFAULT_DATABASE_FOLDER = "chunkpath-data";

    private final Path baseFolder;
    private final Map<String, WorldDatabase> openDatabases = new ConcurrentHashMap<>();

    /** @param pluginDataFolder le dossier de données du plugin (JavaPlugin#getDataFolder()) */
    public ChunkGateStore(Path pluginDataFolder) {
        this(pluginDataFolder, DEFAULT_DATABASE_FOLDER);
    }

    /**
     * @param pluginDataFolder    le dossier de données du plugin (JavaPlugin#getDataFolder())
     * @param databaseFolderName  nom du dossier (dans le dossier du plugin) contenant les
     *                            bases de données par monde ; correspond à la clé de config
     *                            {@code chunkpath.database_folder}
     */
    public ChunkGateStore(Path pluginDataFolder, String databaseFolderName) {
        this.baseFolder = pluginDataFolder.resolve(
                databaseFolderName == null || databaseFolderName.isBlank() ? DEFAULT_DATABASE_FOLDER : databaseFolderName);
    }

    private static String sanitize(String worldName) {
        return worldName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private WorldDatabase database(String worldName) {
        return openDatabases.computeIfAbsent(worldName,
                w -> new WorldDatabase(baseFolder.resolve(sanitize(w) + ".db")));
    }

    /** Test de présence pur. */
    public boolean exists(String worldName, int chunkX, int chunkZ) {
        return database(worldName).contains(chunkX, chunkZ);
    }

    public void save(String worldName, ChunkGateData data) {
        database(worldName).put(data);
    }

    /** @return les données déjà sauvegardées pour ce chunk, ou {@code null} si jamais analysé. */
    public ChunkGateData load(String worldName, int chunkX, int chunkZ) {
        return database(worldName).get(chunkX, chunkZ);
    }

    /** @return toutes les données déjà sauvegardées pour ce monde (aucune analyse déclenchée), liste vide si aucune. */
    public List<ChunkGateData> all(String worldName) {
        return database(worldName).all();
    }

    /** @return {@code true} si un enregistrement existait et a été supprimé, {@code false} sinon. */
    public boolean delete(String worldName, int chunkX, int chunkZ) {
        return database(worldName).remove(chunkX, chunkZ);
    }

    /** Ferme les fichiers ouverts (à appeler depuis l'arrêt du plugin). */
    @Override
    public void close() {
        for (WorldDatabase db : openDatabases.values()) {
            db.close();
        }
        openDatabases.clear();
    }

    // ── une base de données = un fichier ────────────────────────────────────────────

    private static final class WorldDatabase {

        private static final int MAGIC = 0x43504442; // "CPDB"
        private static final int FORMAT_VERSION = 1;
        private static final int HEADER_SIZE = 4 + 4 + 4 + 4; // magic, version, capacity, used
        private static final int SLOT_SIZE = 1 + 8 + 8 + 4; // occupied, key, offset, length
        private static final int INITIAL_CAPACITY = 256;
        private static final double MAX_LOAD_FACTOR = 0.7;

        private final Path file;
        private RandomAccessFile raf;
        private int capacity;
        private final Map<Long, Slot> index = new HashMap<>();
        private boolean[] occupied;

        WorldDatabase(Path file) {
            this.file = file;
        }

        synchronized boolean contains(int chunkX, int chunkZ) {
            ensureOpen();
            return index.containsKey(key(chunkX, chunkZ));
        }

        synchronized ChunkGateData get(int chunkX, int chunkZ) {
            ensureOpen();
            Slot slot = index.get(key(chunkX, chunkZ));
            if (slot == null) return null;
            try {
                byte[] payload = new byte[slot.length];
                raf.seek(slot.offset);
                raf.readFully(payload);
                return deserialize(payload);
            } catch (IOException e) {
                throw new RuntimeException("Impossible de relire les portes du chunk (" + chunkX + "," + chunkZ
                        + ") depuis " + file, e);
            }
        }

        synchronized void put(ChunkGateData data) {
            ensureOpen();
            try {
                long k = key(data.chunkX, data.chunkZ);
                byte[] payload = serialize(data);

                Slot previous = index.get(k);
                if (previous != null && previous.length == payload.length && sameBytesAt(previous, payload)) {
                    return; // contenu strictement identique : rien à réécrire, le fichier ne doit pas grossir
                }

                if (previous == null && (index.size() + 1) > capacity * MAX_LOAD_FACTOR) {
                    growAndCompact(capacity * 2);
                }

                long offset = raf.length();
                raf.seek(offset);
                raf.write(payload);

                int slotIndex = previous != null ? previous.index : allocateSlot(k);
                writeSlot(raf, slotIndex, k, offset, payload.length);
                index.put(k, new Slot(slotIndex, offset, payload.length));

                writeHeader(raf, capacity, index.size());
            } catch (IOException e) {
                throw new RuntimeException("Impossible d'enregistrer les portes du chunk (" + data.chunkX + ","
                        + data.chunkZ + ") dans " + file, e);
            }
        }

        /** @return les données de tous les chunks actuellement présents dans l'index, dans un ordre non spécifié. */
        synchronized List<ChunkGateData> all() {
            ensureOpen();
            List<ChunkGateData> result = new ArrayList<>(index.size());
            try {
                for (Slot slot : index.values()) {
                    byte[] payload = new byte[slot.length];
                    raf.seek(slot.offset);
                    raf.readFully(payload);
                    result.add(deserialize(payload));
                }
            } catch (IOException e) {
                throw new RuntimeException("Impossible de relire tous les chunks depuis " + file, e);
            }
            return result;
        }

        /**
         * Retire l'entrée de l'index (le bloc de données correspondant reste orphelin dans le
         * fichier jusqu'à la prochaine compaction déclenchée par une croissance de l'index,
         * comme pour toute mise à jour — cf. {@link #growAndCompact}).
         */
        synchronized boolean remove(int chunkX, int chunkZ) {
            ensureOpen();
            long k = key(chunkX, chunkZ);
            Slot slot = index.remove(k);
            if (slot == null) return false;
            try {
                occupied[slot.index] = false;
                raf.seek(HEADER_SIZE + (long) slot.index * SLOT_SIZE);
                raf.writeBoolean(false);
                writeHeader(raf, capacity, index.size());
                return true;
            } catch (IOException e) {
                throw new RuntimeException("Impossible de supprimer l'enregistrement du chunk (" + chunkX + ","
                        + chunkZ + ") dans " + file, e);
            }
        }

        synchronized void close() {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException ignored) {
                    // rien à faire de plus, le fichier sera simplement relu/rouvert au besoin
                } finally {
                    raf = null;
                }
            }
        }

        private void ensureOpen() {
            if (raf != null) return;
            try {
                Files.createDirectories(file.getParent());
                boolean isNew = !Files.exists(file) || Files.size(file) == 0;
                raf = new RandomAccessFile(file.toFile(), "rw");
                if (isNew) {
                    initEmpty(INITIAL_CAPACITY);
                } else {
                    loadIndex();
                }
            } catch (IOException e) {
                throw new RuntimeException("Impossible d'ouvrir la base de données " + file, e);
            }
        }

        private void initEmpty(int cap) throws IOException {
            capacity = cap;
            occupied = new boolean[capacity];
            index.clear();
            raf.setLength(0);
            writeHeader(raf, capacity, 0);
            writeZeros(raf, HEADER_SIZE, (long) capacity * SLOT_SIZE);
        }

        private void loadIndex() throws IOException {
            raf.seek(0);
            int magic = raf.readInt();
            if (magic != MAGIC) {
                throw new IOException("Base de données invalide (magic incorrect) : " + file);
            }
            int version = raf.readInt();
            if (version != FORMAT_VERSION) {
                throw new IOException("Version de format non supportée (" + version + ") : " + file);
            }
            capacity = raf.readInt();
            int used = raf.readInt();

            occupied = new boolean[capacity];
            index.clear();
            byte[] region = new byte[capacity * SLOT_SIZE];
            raf.seek(HEADER_SIZE);
            raf.readFully(region);

            DataInputStream in = new DataInputStream(new ByteArrayInputStream(region));
            for (int i = 0; i < capacity; i++) {
                boolean slotOccupied = in.readBoolean();
                long k = in.readLong();
                long offset = in.readLong();
                int length = in.readInt();
                if (slotOccupied) {
                    occupied[i] = true;
                    index.put(k, new Slot(i, offset, length));
                }
            }
            if (index.size() != used) {
                throw new IOException("Base de données corrompue (index incohérent) : " + file);
            }
        }

        /** @return {@code true} si les octets déjà stockés pour {@code slot} sont identiques à {@code payload}. */
        private boolean sameBytesAt(Slot slot, byte[] payload) throws IOException {
            byte[] existing = new byte[slot.length];
            raf.seek(slot.offset);
            raf.readFully(existing);
            return Arrays.equals(existing, payload);
        }

        private int allocateSlot(long k) {
            int i = Math.floorMod(Long.hashCode(k), capacity);
            while (occupied[i]) {
                i = (i + 1) % capacity;
            }
            occupied[i] = true;
            return i;
        }

        /** Réécrit tout le fichier avec une table d'index plus grande, ne gardant que la dernière version de chaque chunk. */
        private void growAndCompact(int newCapacity) throws IOException {
            List<Map.Entry<Long, byte[]>> live = new ArrayList<>(index.size());
            for (Map.Entry<Long, Slot> e : index.entrySet()) {
                Slot slot = e.getValue();
                byte[] payload = new byte[slot.length];
                raf.seek(slot.offset);
                raf.readFully(payload);
                live.add(Map.entry(e.getKey(), payload));
            }

            Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
            try (RandomAccessFile tmpRaf = new RandomAccessFile(tmp.toFile(), "rw")) {
                tmpRaf.setLength(0);
                writeHeader(tmpRaf, newCapacity, live.size());
                writeZeros(tmpRaf, HEADER_SIZE, (long) newCapacity * SLOT_SIZE);

                boolean[] newOccupied = new boolean[newCapacity];
                Map<Long, Slot> newIndex = new HashMap<>();
                long writeOffset = HEADER_SIZE + (long) newCapacity * SLOT_SIZE;

                for (Map.Entry<Long, byte[]> e : live) {
                    long k = e.getKey();
                    byte[] payload = e.getValue();

                    tmpRaf.seek(writeOffset);
                    tmpRaf.write(payload);

                    int i = Math.floorMod(Long.hashCode(k), newCapacity);
                    while (newOccupied[i]) {
                        i = (i + 1) % newCapacity;
                    }
                    newOccupied[i] = true;
                    writeSlot(tmpRaf, i, k, writeOffset, payload.length);
                    newIndex.put(k, new Slot(i, writeOffset, payload.length));

                    writeOffset += payload.length;
                }
            }

            raf.close();
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            raf = new RandomAccessFile(file.toFile(), "rw");
            loadIndex();
        }

        private static void writeHeader(RandomAccessFile raf, int capacity, int used) throws IOException {
            raf.seek(0);
            raf.writeInt(MAGIC);
            raf.writeInt(FORMAT_VERSION);
            raf.writeInt(capacity);
            raf.writeInt(used);
        }

        private static void writeSlot(RandomAccessFile raf, int slotIndex, long key, long offset, int length)
                throws IOException {
            raf.seek(HEADER_SIZE + (long) slotIndex * SLOT_SIZE);
            raf.writeBoolean(true);
            raf.writeLong(key);
            raf.writeLong(offset);
            raf.writeInt(length);
        }

        private static void writeZeros(RandomAccessFile raf, long from, long length) throws IOException {
            raf.seek(from);
            byte[] chunk = new byte[Math.min(8192, (int) Math.min(length, Integer.MAX_VALUE))];
            long remaining = length;
            while (remaining > 0) {
                int n = (int) Math.min(chunk.length, remaining);
                raf.write(chunk, 0, n);
                remaining -= n;
            }
        }

        private static long key(int chunkX, int chunkZ) {
            return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
        }

        private record Slot(int index, long offset, int length) {
        }
    }

    // ── (dé)sérialisation des portes d'un chunk (inchangée dans son contenu) ────────

    private static byte[] serialize(ChunkGateData data) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(data.chunkX);
            out.writeInt(data.chunkZ);

            List<Gate> gates = data.gates();
            out.writeInt(gates.size());
            for (Gate g : gates) {
                out.writeInt(g.id);
                out.writeByte(g.edge.ordinal());
                out.writeBoolean(g.deadEndCandidate);

                out.writeInt(g.memberBlocksWorld.size());
                for (int[] b : g.memberBlocksWorld) {
                    out.writeInt(b[0]);
                    out.writeInt(b[1]);
                    out.writeInt(b[2]);
                }

                out.writeInt(g.representativeWorld[0]);
                out.writeInt(g.representativeWorld[1]);
                out.writeInt(g.representativeWorld[2]);

                out.writeInt(g.internalDistances.size());
                for (var e : g.internalDistances.entrySet()) {
                    out.writeInt(e.getKey());
                    out.writeInt(e.getValue());
                }

                out.writeInt(g.neighborLinks.size());
                for (String link : g.neighborLinks) {
                    out.writeUTF(link);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e); // écriture en mémoire : ne peut pas arriver
        }
        return buffer.toByteArray();
    }

    private static ChunkGateData deserialize(byte[] payload) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int chunkX = in.readInt();
            int chunkZ = in.readInt();

            int gateCount = in.readInt();
            List<Gate> gates = new ArrayList<>(gateCount);
            for (int i = 0; i < gateCount; i++) {
                int id = in.readInt();
                Direction edge = Direction.values()[in.readByte()];
                boolean deadEndCandidate = in.readBoolean();

                int memberCount = in.readInt();
                List<int[]> members = new ArrayList<>(memberCount);
                for (int m = 0; m < memberCount; m++) {
                    members.add(new int[]{in.readInt(), in.readInt(), in.readInt()});
                }

                int[] representative = {in.readInt(), in.readInt(), in.readInt()};

                Gate gate = new Gate(chunkX, chunkZ, id, edge, members, representative);
                gate.deadEndCandidate = deadEndCandidate;

                int distanceCount = in.readInt();
                for (int d = 0; d < distanceCount; d++) {
                    int otherId = in.readInt();
                    int dist = in.readInt();
                    gate.internalDistances.put(otherId, dist);
                }

                int linkCount = in.readInt();
                for (int l = 0; l < linkCount; l++) {
                    gate.neighborLinks.add(in.readUTF());
                }

                gates.add(gate);
            }

            return new ChunkGateData(chunkX, chunkZ, gates);
        } catch (IOException e) {
            throw new RuntimeException("Enregistrement de chunk corrompu", e);
        }
    }
}
