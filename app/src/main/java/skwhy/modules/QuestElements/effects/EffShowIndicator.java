package skwhy.modules.QuestElements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.model.QuestIndicator;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.npc.IndicatorService;

@Name("[Quest] Show Quest Indicator")
@Description("Forces the marker shown above a quest NPC to some players, whatever its script says ('none' hides it). " +
    "'stop showing' removes the forced marker so the marker computed from the script is used again.")
@Examples({
    "show quest indicator new above quest npc \"maire\" to player",
    "show quest indicator none above quest npc \"maire\" to player",
    "stop showing quest indicator above quest npc \"maire\" to player"
})
@Since("1.6.0")
public class EffShowIndicator extends Effect {

    private @Nullable Expression<QuestIndicator> indicatorExpr;
    private Expression<QuestNpc> npcExpr;
    private Expression<Player> playersExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffShowIndicator.class)
                .addPattern("show quest indicator %questindicator% above %questnpc% to %players%")
                .addPattern("stop showing quest indicator above %questnpc% to %players%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        if (matchedPattern == 0) {
            indicatorExpr = (Expression<QuestIndicator>) exprs[0];
            npcExpr = (Expression<QuestNpc>) exprs[1];
            playersExpr = (Expression<Player>) exprs[2];
        } else {
            npcExpr = (Expression<QuestNpc>) exprs[0];
            playersExpr = (Expression<Player>) exprs[1];
        }
        return true;
    }

    @Override
    protected void execute(Event event) {
        QuestNpc npc = npcExpr.getSingle(event);
        if (npc == null) return;
        QuestIndicator indicator = indicatorExpr == null ? null : indicatorExpr.getSingle(event);
        if (indicatorExpr != null && indicator == null) return;
        for (Player player : playersExpr.getArray(event)) IndicatorService.pin(player.getUniqueId(), npc.id(), indicator);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return indicatorExpr == null
            ? "stop showing quest indicator above " + npcExpr.toString(event, debug) + " to " + playersExpr.toString(event, debug)
            : "show quest indicator " + indicatorExpr.toString(event, debug) + " above " + npcExpr.toString(event, debug) + " to " + playersExpr.toString(event, debug);
    }
}
