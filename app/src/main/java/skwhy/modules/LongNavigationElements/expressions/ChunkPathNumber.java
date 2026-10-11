package skwhy.modules.LongNavigationElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.modules.LongNavigationElements.types.ChunkPathRecord;

@Name("[ChunkPath] Numeric Properties")
@Description("Gets numeric properties of a chunk path record: its chunk X/Z coordinates, or its gate count. Read-only.")
@Examples({
    "set {_record} to chunk path record of target block's chunk",
    "if {_record} is set:",
    "\tbroadcast \"chunk (%chunk x of chunk path {_record}%, %chunk z of chunk path {_record}%) has %gate count of chunk path {_record}% gate(s)\""
})
@Since("1.4.0")
public class ChunkPathNumber extends SimpleExpression<Number> {

    private int matchedPattern;
    private Expression<ChunkPathRecord> recordExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.matchedPattern = matchedPattern;
        this.recordExpr = (Expression<ChunkPathRecord>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Number[] get(Event event) {
        ChunkPathRecord record = recordExpr.getSingle(event);
        if (record == null) return null;

        Number value = switch (matchedPattern) {
            case 0 -> record.getChunkX();
            case 1 -> record.getChunkZ();
            case 2 -> record.getGateCount();
            default -> null;
        };
        return value != null ? new Number[]{ value } : null;
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Number> getReturnType() { return Number.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return switch (matchedPattern) {
            case 0 -> "chunk x of chunk path " + recordExpr.toString(event, debug);
            case 1 -> "chunk z of chunk path " + recordExpr.toString(event, debug);
            case 2 -> "gate count of chunk path " + recordExpr.toString(event, debug);
            default -> "chunk path number";
        };
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ChunkPathNumber.class, Number.class)
                .addPattern("chunk x of chunk path %chunkpathrecord%")
                .addPattern("chunk z of chunk path %chunkpathrecord%")
                .addPattern("gate count of chunk path %chunkpathrecord%")
                .build()
        );
    }
}
