package skwhy.modules.LongNavigationElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.chunkpath.gate.Gate;
import skwhy.modules.LongNavigationElements.types.ChunkPathRecord;

import java.util.LinkedHashSet;
import java.util.Set;

@Name("[ChunkPath] Linked Chunks")
@Description("Returns every neighboring chunk connected to at least one gate of a chunk path record (i.e. chunks " +
    "reachable from it according to the chunk-to-chunk analysis), as a list of chunks. Read-only. May load those " +
    "chunks if they aren't currently loaded. Records whose world is no longer loaded are silently skipped.")
@Examples({
    "set {_record} to chunk path record of target block's chunk",
    "if {_record} is set:",
    "\tloop linked chunks of chunk path {_record}:",
    "\t\tbroadcast \"linked to %loop-value%\""
})
@Since("1.4.0")
public class ChunkPathLinkedChunks extends SimpleExpression<Chunk> {

    private Expression<ChunkPathRecord> recordExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.recordExpr = (Expression<ChunkPathRecord>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Chunk[] get(Event event) {
        ChunkPathRecord[] records = recordExpr.getAll(event);
        if (records == null || records.length == 0) return null;

        Set<Chunk> chunks = new LinkedHashSet<>();
        for (ChunkPathRecord record : records) {
            World world = Bukkit.getWorld(record.getWorldName());
            if (world == null) continue;

            for (Gate gate : record.getData().gates()) {
                for (String link : gate.neighborLinks) {
                    String[] parts = link.split(",");
                    if (parts.length < 2) continue;
                    try {
                        int chunkX = Integer.parseInt(parts[0]);
                        int chunkZ = Integer.parseInt(parts[1]);
                        chunks.add(world.getChunkAt(chunkX, chunkZ));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return chunks.toArray(new Chunk[0]);
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends Chunk> getReturnType() { return Chunk.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "linked chunks of chunk path " + recordExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ChunkPathLinkedChunks.class, Chunk.class)
                .addPattern("linked chunks of chunk path %chunkpathrecord%")
                .build()
        );
    }
}
