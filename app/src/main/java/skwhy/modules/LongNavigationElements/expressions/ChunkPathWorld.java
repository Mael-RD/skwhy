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
import org.bukkit.World;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.modules.LongNavigationElements.types.ChunkPathRecord;

@Name("[ChunkPath] World")
@Description("Returns the world a chunk path record belongs to. Read-only. " +
    "Returns nothing if that world is no longer loaded.")
@Examples({
    "set {_record} to chunk path record of target block's chunk",
    "if {_record} is set:",
    "\tbroadcast \"world: %world of chunk path {_record}%\""
})
@Since("1.4.0")
public class ChunkPathWorld extends SimpleExpression<World> {

    private Expression<ChunkPathRecord> recordExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.recordExpr = (Expression<ChunkPathRecord>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable World[] get(Event event) {
        ChunkPathRecord record = recordExpr.getSingle(event);
        if (record == null) return null;
        World world = Bukkit.getWorld(record.getWorldName());
        return world != null ? new World[]{ world } : null;
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends World> getReturnType() { return World.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "world of chunk path " + recordExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ChunkPathWorld.class, World.class)
                .addPattern("world of chunk path %chunkpathrecord%")
                .build()
        );
    }
}
