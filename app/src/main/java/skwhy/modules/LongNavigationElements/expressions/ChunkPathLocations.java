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
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.chunkpath.gate.Gate;
import skwhy.modules.LongNavigationElements.types.ChunkPathRecord;

import java.util.ArrayList;
import java.util.List;

@Name("[ChunkPath] Locations")
@Description("Returns block locations extracted from one or more chunk path records, in a form native to Skript " +
    "(a list of locations): the representative point of every gate, every single border block belonging to a " +
    "gate, or only the representative points of gates still considered dead ends. Read-only. " +
    "Records whose world is no longer loaded are silently skipped.")
@Examples({
    "set {_record} to chunk path record of target block's chunk",
    "if {_record} is set:",
    "\tloop gate locations of chunk path {_record}:",
    "\t\tbroadcast \"gate at %loop-value%\"",
    "",
    "\tset {_border::*} to border block locations of chunk path {_record}",
    "\tset {_deadends::*} to dead end gate locations of chunk path {_record}"
})
@Since("1.4.0")
public class ChunkPathLocations extends SimpleExpression<Location> {

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
    protected @Nullable Location[] get(Event event) {
        ChunkPathRecord[] records = recordExpr.getAll(event);
        if (records == null || records.length == 0) return null;

        List<Location> locations = new ArrayList<>();
        for (ChunkPathRecord record : records) {
            World world = Bukkit.getWorld(record.getWorldName());
            if (world == null) continue;

            for (Gate gate : record.getData().gates()) {
                switch (matchedPattern) {
                    case 0 -> locations.add(toLocation(world, gate.representativeWorld));
                    case 1 -> {
                        for (int[] block : gate.memberBlocksWorld) {
                            locations.add(toLocation(world, block));
                        }
                    }
                    case 2 -> {
                        if (gate.deadEndCandidate) {
                            locations.add(toLocation(world, gate.representativeWorld));
                        }
                    }
                }
            }
        }
        return locations.toArray(new Location[0]);
    }

    private static Location toLocation(World world, int[] block) {
        return new Location(world, block[0], block[1], block[2]);
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends Location> getReturnType() { return Location.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return switch (matchedPattern) {
            case 0 -> "gate locations of chunk path " + recordExpr.toString(event, debug);
            case 1 -> "border block locations of chunk path " + recordExpr.toString(event, debug);
            case 2 -> "dead end gate locations of chunk path " + recordExpr.toString(event, debug);
            default -> "chunk path locations";
        };
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ChunkPathLocations.class, Location.class)
                .addPattern("gate locations of chunk path %chunkpathrecord%")
                .addPattern("border block locations of chunk path %chunkpathrecord%")
                .addPattern("dead end gate locations of chunk path %chunkpathrecord%")
                .build()
        );
    }
}
