package skwhy.modules.LongNavigationElements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.Skript;
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

import skwhy.SkWhy;
import skwhy.chunkpath.ChunkGateService;
import skwhy.chunkpath.block.PathCostRules;
import skwhy.modules.LongNavigationElements.ChunkPathManager;
import skwhy.modules.LongNavigationElements.types.PathCostRuleData;

import java.util.ArrayList;
import java.util.List;

@Name("[ChunkPath] Register Chunk Path")
@Description("Analyzes one or more chunks and (re)registers their chunk path records (their gates to neighboring chunks) in the " +
    "world's chunk path database. Unlike a normal lookup, this always re-runs the analysis and overwrites any " +
    "record already saved for those chunks. The chunk must already be generated on disk (never generates it). " +
    "Requires one or more path cost rules (built via 'a new path cost rule'): there are no default rules, and without " +
    "any rule a warning is logged and nothing is registered. A rule " +
    "without any block or tag is the default rule, applied to every block no other rule covers; without one, such " +
    "blocks are not walkable. Crossing a gate between two chunks costs 0. The terrain is read from the world's region files on disk, " +
    "not from the loaded chunks: blocks changed since the chunk was last saved are not seen until the world is saved. " +
    "This can be a heavy operation on the calling thread — avoid registering many chunks at once from the main thread.")
@Examples({
    "set {_rule} to a new path cost rule with cost 1",
    "add stone and cobblestone to blocks of path cost rule {_rule}",
    "register chunk path of target block's chunk using rules {_rule}",
    "register chunk path of player's chunk using rules {_rule}"
})
@Since("1.5.0")
public class RegisterChunkPath extends Effect {

    private Expression<Chunk> chunksExpr;
    private Expression<PathCostRuleData> rulesExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.chunksExpr = (Expression<Chunk>) exprs[0];
        this.rulesExpr = exprs.length > 1 ? (Expression<PathCostRuleData>) exprs[1] : null;
        return true;
    }

    @Override
    protected void execute(Event event) {
        PathCostRules customRules = buildCustomRules(event);
        if (customRules == null) {
            Skript.warning("[ChunkPath] 'register chunk path' sans règle de coût : il n'y a pas de règle par défaut, "
                    + "rien n'a été enregistré. Utilisez 'using rules ...'.");
            return;
        }
        for (Chunk chunk : chunksExpr.getArray(event)) {
            if (chunk == null) continue;
            ChunkGateService service = ChunkPathManager.serviceFor(chunk.getWorld());
            try {
                service.reanalyze(chunk.getX(), chunk.getZ(), customRules);
            } catch (RuntimeException e) {
                SkWhy.getInstance().getLogger().warning(
                        "[ChunkPath] Impossible d'enregistrer le chunk (" + chunk.getX() + "," + chunk.getZ()
                                + ") du monde " + chunk.getWorld().getName() + " : " + e.getMessage());
            }
        }
    }

    /** @return les règles de coût assemblées depuis la liste fournie au script, ou {@code null} s'il n'y en a pas. */
    private @Nullable PathCostRules buildCustomRules(Event event) {
        if (rulesExpr == null) return null;
        return PathCostRuleData.buildRules(rulesExpr.getAll(event));
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        String base = "register chunk path of " + chunksExpr.toString(event, debug);
        return rulesExpr != null ? base + " using rules " + rulesExpr.toString(event, debug) : base;
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(RegisterChunkPath.class)
                .addPattern("register [the] chunk path [record] of %chunks% [using rules %-pathcostrules%]")
                .build()
        );
    }
}
