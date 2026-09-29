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

import skwhy.SkWhy;
import skwhy.chunkpath.ChunkGateService;
import skwhy.chunkpath.block.PathCostRules;
import skwhy.chunkpath.region.ChunkNotGeneratedException;
import skwhy.modules.LongNavigationElements.ChunkPathManager;
import skwhy.modules.LongNavigationElements.types.PathCostRuleData;

import java.util.ArrayList;
import java.util.List;

@Name("[ChunkPath] Register Chunk Path")
@Description("Analyzes a chunk and (re)registers its chunk path record (its gates to neighboring chunks) in the " +
    "world's chunk path database. Unlike a normal lookup, this always re-runs the analysis and overwrites any " +
    "record already saved for that chunk. The chunk must already be generated on disk (never generates it). " +
    "Optionally accepts one or more custom path cost rules (built via 'a new path cost rule') to use for this " +
    "analysis instead of the world's default cost rules. " +
    "This can be a heavy operation on the calling thread — avoid registering many chunks at once from the main thread.")
@Examples({
    "register chunk path of target block's chunk",
    "register chunk path of player's chunk",
    "",
    "set {_rule} to a new path cost rule with cost 1",
    "add stone and cobblestone to blocks of path cost rule {_rule}",
    "register chunk path of target block's chunk using rules {_rule}"
})
@Since("1.4.0")
public class RegisterChunkPath extends Effect {

    private Expression<Chunk> chunkExpr;
    private Expression<PathCostRuleData> rulesExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.chunkExpr = (Expression<Chunk>) exprs[0];
        this.rulesExpr = exprs.length > 1 ? (Expression<PathCostRuleData>) exprs[1] : null;
        return true;
    }

    @Override
    protected void execute(Event event) {
        Chunk chunk = chunkExpr.getSingle(event);
        if (chunk == null) return;

        ChunkGateService service = ChunkPathManager.serviceFor(chunk.getWorld());
        PathCostRules customRules = buildCustomRules(event);
        try {
            service.reanalyze(chunk.getX(), chunk.getZ(), customRules);
        } catch (ChunkNotGeneratedException e) {
            SkWhy.getInstance().getLogger().warning(
                    "[ChunkPath] Impossible d'enregistrer le chunk (" + chunk.getX() + "," + chunk.getZ()
                            + ") du monde " + chunk.getWorld().getName() + " : " + e.getMessage());
        }
    }

    /** @return les règles de coût assemblées depuis la liste fournie au script, ou {@code null} pour garder celles par défaut du monde. */
    private @Nullable PathCostRules buildCustomRules(Event event) {
        if (rulesExpr == null) return null;
        PathCostRuleData[] ruleDatas = rulesExpr.getAll(event);
        if (ruleDatas == null || ruleDatas.length == 0) return null;

        List<PathCostRules.CostRule> costRules = new ArrayList<>(ruleDatas.length);
        for (PathCostRuleData ruleData : ruleDatas) {
            if (ruleData != null) costRules.add(ruleData.toCostRule());
        }
        return new PathCostRules(costRules, PathCostRules.defaults().defaultCost());
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        String base = "register chunk path of " + chunkExpr.toString(event, debug);
        return rulesExpr != null ? base + " using rules " + rulesExpr.toString(event, debug) : base;
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(RegisterChunkPath.class)
                .addPattern("register [the] chunk path [record] of %chunk% [using rules %-pathcostrules%]")
                .build()
        );
    }
}
