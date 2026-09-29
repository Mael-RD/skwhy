package skwhy.modules.LongNavigationElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.chunkpath.ChunkGateService;
import skwhy.chunkpath.gate.ChunkGateData;
import skwhy.modules.LongNavigationElements.ChunkPathManager;
import skwhy.modules.LongNavigationElements.types.ChunkPathRecord;

import java.util.List;

@Name("[ChunkPath] All Chunk Paths")
@Description("Returns every chunk path record already registered for a world, read directly from its database. " +
    "Never triggers an analysis by itself — use 'register chunk path of %chunk%' for that. " +
    "Returns nothing if no chunk has ever been registered for that world.")
@Examples({
    "loop all chunk path records of world \"world\":",
    "\tbroadcast \"%loop-value%\""
})
@Since("1.4.0")
public class ChunkPathAllOf extends SimpleExpression<ChunkPathRecord> {

    private Expression<World> worldExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.worldExpr = (Expression<World>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable ChunkPathRecord[] get(Event event) {
        World world = worldExpr.getSingle(event);
        if (world == null) return null;

        ChunkGateService service = ChunkPathManager.serviceFor(world);
        List<ChunkGateData> all = service.allKnown();
        if (all.isEmpty()) return null;

        ChunkPathRecord[] records = new ChunkPathRecord[all.size()];
        for (int i = 0; i < all.size(); i++) {
            records[i] = new ChunkPathRecord(world.getName(), all.get(i));
        }
        return records;
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends ChunkPathRecord> getReturnType() { return ChunkPathRecord.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "all chunk path records of " + worldExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ChunkPathAllOf.class, ChunkPathRecord.class)
                .addPattern("all chunk path[s] [record[s]] of %world%")
                .build()
        );
    }
}
