package skwhy.modules.LongNavigationElements.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.chunkpath.ChunkGateService;
import skwhy.chunkpath.block.PathCostRules;
import skwhy.chunkpath.gate.Gate;
import skwhy.chunkpath.pathfinding.GatePathfinder;
import skwhy.modules.LongNavigationElements.ChunkPathManager;
import skwhy.modules.LongNavigationElements.types.PathCostRuleData;

import java.util.List;

@Name("[ChunkPath] Gate Path Locations")
@Description("Computes the shortest chunk-to-chunk gate path between two locations and returns the representative " +
    "location of every gate crossed along the way, in order (start -> end). Reuses only chunk path records already " +
    "known/analyzed for the crossed chunks: a gate leading to a chunk that was never registered is treated as a " +
    "dead end and ignored, it never triggers a chunk analysis by itself. Requires path cost rules ('using rules'): there " +
    "are no default rules, without any a warning is logged and nothing is returned. Returns nothing if the two locations " +
    "aren't in the same world, if either isn't on/near practicable ground, or if no path could be found.")
@Examples({
    "set {_rule} to a new path cost rule with cost 5",
    "loop gate path locations from {_start} to {_end} using rules {_rule}:",
    "\tbroadcast \"gate at %loop-value%\""
})
@Since("1.5.0")
public class GatePathLocations extends SimpleExpression<Location> {

    private Expression<Location> fromExpr;
    private Expression<Location> toExpr;
    private Expression<PathCostRuleData> rulesExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.fromExpr = (Expression<Location>) exprs[0];
        this.toExpr = (Expression<Location>) exprs[1];
        this.rulesExpr = (Expression<PathCostRuleData>) exprs[2];
        return true;
    }

    @Override
    protected @Nullable Location[] get(Event event) {
        Location from = fromExpr.getSingle(event);
        Location to = toExpr.getSingle(event);
        if (from == null || to == null) return null;

        World world = from.getWorld();
        if (world == null || to.getWorld() == null || !world.equals(to.getWorld())) return null;

        PathCostRules rules = rulesExpr != null ? PathCostRuleData.buildRules(rulesExpr.getAll(event)) : null;
        if (rules == null) {
            Skript.warning("[ChunkPath] 'gate path locations' sans règle de coût : il n'y a pas de règle par défaut, "
                    + "aucun chemin calculé. Utilisez 'using rules ...'.");
            return null;
        }

        List<Gate> path;
        try {
            ChunkGateService service = ChunkPathManager.serviceFor(world);
            path = GatePathfinder.findPath(service, rules,
                    from.getBlockX(), from.getBlockY(), from.getBlockZ(),
                    to.getBlockX(), to.getBlockY(), to.getBlockZ());
        } catch (RuntimeException e) {
            skwhy.SkWhy.getInstance().getLogger().warning("[ChunkPath] Recherche de chemin de portes impossible dans le monde "
                    + world.getName() + " : " + e);
            return null;
        }
        if (path == null) return null;

        Location[] locations = new Location[path.size()];
        for (int i = 0; i < path.size(); i++) {
            int[] representative = path.get(i).representativeWorld;
            locations[i] = new Location(world, representative[0], representative[1], representative[2]);
        }
        return locations;
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends Location> getReturnType() { return Location.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        String base = "gate path locations from " + fromExpr.toString(event, debug) + " to " + toExpr.toString(event, debug);
        return rulesExpr != null ? base + " using rules " + rulesExpr.toString(event, debug) : base;
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(GatePathLocations.class, Location.class)
                .addPattern("gate path locations (from|between) %location% (to|and) %location% [using rules %-pathcostrules%]")
                .build()
        );
    }
}
