package skwhy.modules.LongNavigationElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.Chunk;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.chunkpath.ChunkGateService;
import skwhy.chunkpath.gate.ChunkGateData;
import skwhy.modules.LongNavigationElements.ChunkPathManager;
import skwhy.modules.LongNavigationElements.types.ChunkPathRecord;

@Name("[ChunkPath] Chunk Path Record")
@Description("Returns the chunk path record already registered for a chunk (its analyzed gates), " +
    "or nothing if that chunk has never been registered. Never triggers an analysis by itself — " +
    "use 'register chunk path of %chunk%' for that.")
@Examples({
    "set {_record} to chunk path record of target block's chunk",
    "if {_record} is not set:",
    "\tregister chunk path of target block's chunk",
    "\tset {_record} to chunk path record of target block's chunk"
})
@Since("1.4.0")
public class ChunkPathOf extends SimpleExpression<ChunkPathRecord> {

    private Expression<Chunk> chunkExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, ParseResult pr) {
        this.chunkExpr = (Expression<Chunk>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable ChunkPathRecord[] get(Event event) {
        Chunk chunk = chunkExpr.getSingle(event);
        if (chunk == null) return null;

        ChunkGateService service = ChunkPathManager.serviceFor(chunk.getWorld());
        ChunkGateData data = service.getIfKnown(chunk.getX(), chunk.getZ());
        if (data == null) return null;

        return new ChunkPathRecord[]{ new ChunkPathRecord(chunk.getWorld().getName(), data) };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends ChunkPathRecord> getReturnType() { return ChunkPathRecord.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "chunk path record of " + chunkExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ChunkPathOf.class, ChunkPathRecord.class)
                .addPattern("chunk path [record] of %chunk%")
                .build()
        );
    }
}
