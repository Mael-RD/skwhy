package skwhy.modules.LongNavigationElements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.Chunk;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.chunkpath.ChunkGateService;
import skwhy.modules.LongNavigationElements.ChunkPathManager;

@Name("[ChunkPath] Delete Chunk Path")
@Description("Deletes the chunk path record of a chunk (from memory and from the world's chunk path database), " +
    "if any. Does nothing if that chunk was never registered.")
@Examples({
    "delete chunk path of target block's chunk",
    "delete chunk path record of player's chunk"
})
@Since("1.4.0")
public class DeleteChunkPath extends Effect {

    private Expression<Chunk> chunkExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.chunkExpr = (Expression<Chunk>) exprs[0];
        return true;
    }

    @Override
    protected void execute(Event event) {
        Chunk chunk = chunkExpr.getSingle(event);
        if (chunk == null) return;

        ChunkGateService service = ChunkPathManager.serviceFor(chunk.getWorld());
        service.delete(chunk.getX(), chunk.getZ());
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "delete chunk path of " + chunkExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(DeleteChunkPath.class)
                .addPattern("delete [the] chunk path [record] of %chunk%")
                .build()
        );
    }
}
