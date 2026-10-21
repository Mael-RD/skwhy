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
import skwhy.quest.model.QuestNpc;
import skwhy.quest.npc.NpcService;

@Name("[Quest] Show / Hide Quest NPC")
@Description("Hides a quest NPC from players (all its positions), or shows it again at the position they normally see it. " +
    "Hiding stays until the NPC is shown or the players leave; it does not delete anything.")
@Examples({
    "hide quest npc \"maire\" from player",
    "show quest npc \"maire\" to player"
})
@Since("1.6.0")
public class EffShowHideNpc extends Effect {

    private Expression<QuestNpc> npcExpr;
    private Expression<Player> playersExpr;
    private boolean hide;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffShowHideNpc.class)
                .addPattern("show %questnpc% to %players%")
                .addPattern("hide %questnpc% from %players%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        npcExpr = (Expression<QuestNpc>) exprs[0];
        playersExpr = (Expression<Player>) exprs[1];
        hide = matchedPattern == 1;
        return true;
    }

    @Override
    protected void execute(Event event) {
        QuestNpc npc = npcExpr.getSingle(event);
        if (npc == null) return;
        for (Player player : playersExpr.getArray(event)) {
            if (hide) NpcService.suspend(player.getUniqueId(), npc.id());
            else NpcService.resume(player.getUniqueId(), npc.id());
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return hide ? "hide " + npcExpr.toString(event, debug) + " from " + playersExpr.toString(event, debug)
            : "show " + npcExpr.toString(event, debug) + " to " + playersExpr.toString(event, debug);
    }
}
