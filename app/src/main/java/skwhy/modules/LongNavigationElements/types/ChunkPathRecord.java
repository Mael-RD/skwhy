package skwhy.modules.LongNavigationElements.types;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.classes.Serializer;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.yggdrasil.Fields;
import skwhy.chunkpath.gate.ChunkGateData;
import skwhy.chunkpath.gate.Gate;

import java.io.StreamCorruptedException;
import java.util.List;

/**
 * Enregistrement (chunk path) résultant de l'analyse chunk-à-chunk d'un chunk : le
 * monde concerné et les portes calculées ({@link ChunkGateData}).
 */
public final class ChunkPathRecord {

    private final String worldName;
    private final ChunkGateData data;

    public ChunkPathRecord(String worldName, ChunkGateData data) {
        this.worldName = worldName;
        this.data = data;
    }

    public String getWorldName() {
        return worldName;
    }

    public ChunkGateData getData() {
        return data;
    }

    public int getChunkX() {
        return data.chunkX;
    }

    public int getChunkZ() {
        return data.chunkZ;
    }

    public int getGateCount() {
        return data.gates().size();
    }

    @Override
    public String toString() {
        return "ChunkPathRecord[" + worldName + " " + data.chunkX + "," + data.chunkZ
                + ", " + data.gates().size() + " gate(s)]";
    }

    /** Représentation complète (monde, coordonnées, et détail de chaque porte) utilisée pour le débogage Skript. */
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ChunkPathRecord[world=").append(worldName)
          .append(", chunk=(").append(data.chunkX).append(',').append(data.chunkZ).append(')')
          .append(", gates=").append(data.gates().size());

        List<Gate> gates = data.gates();
        for (int i = 0; i < gates.size(); i++) {
            Gate g = gates.get(i);
            sb.append("\n  #").append(i).append(" id=").append(g.id)
              .append(" edge=").append(g.edge)
              .append(" deadEndCandidate=").append(g.deadEndCandidate)
              .append(" members=").append(g.memberBlocksWorld.size())
              .append(" representative=(").append(g.representativeWorld[0]).append(',')
                  .append(g.representativeWorld[1]).append(',').append(g.representativeWorld[2]).append(')')
              .append(" internalDistances=").append(g.internalDistances)
              .append(" neighborLinks=").append(g.neighborLinks);
        }
        sb.append(']');
        return sb.toString();
    }

    // =========================================================================
    // Enregistrement du type Skript
    // =========================================================================

    public static void register() {
        Classes.registerClass(new ClassInfo<>(ChunkPathRecord.class, "chunkpathrecord")
            .name("Chunk Path Record")
            .description(
                "Represents the chunk-to-chunk pathfinding record of an analyzed chunk: the world it belongs to " +
                "and the gates (connections to neighboring chunks) computed for it. " +
                "Obtained via 'chunk path [record] of %chunk%', which returns nothing if the chunk was never registered."
            )
            .usage("Obtained via 'chunk path [record] of %chunk%'.")
            .user("chunk ?paths?")
            .examples(
                "set {_record} to chunk path record of target block's chunk",
                "if {_record} is set:",
                "\tbroadcast \"%{_record}%\""
            )
            .since("1.4.0")

            .parser(new Parser<>() {
                @Override
                public ChunkPathRecord parse(String s, ParseContext context) {
                    return null; // non parsable depuis du texte
                }

                @Override
                public boolean canParse(ParseContext context) {
                    return false;
                }

                @Override
                public String toString(ChunkPathRecord record, int flags) {
                    return record.toString();
                }

                @Override
                public String toVariableNameString(ChunkPathRecord record) {
                    return record.toString();
                }

                @Override
                public String getDebugMessage(ChunkPathRecord record) {
                    return record.toDebugString();
                }
            })

            .serializer(new Serializer<>() {
                @Override
                public Fields serialize(ChunkPathRecord record) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void deserialize(ChunkPathRecord record, Fields f) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public ChunkPathRecord deserialize(Fields fields) throws StreamCorruptedException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean mustSyncDeserialization() {
                    return false;
                }

                @Override
                protected boolean canBeInstantiated() {
                    return false;
                }
            })
        );
    }
}
