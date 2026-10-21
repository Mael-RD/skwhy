package skwhy.modules.QuestElements.effects;

import ch.njol.skript.Skript;
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
import skwhy.quest.npc.NpcMover;

@Name("[Quest] Move Quest NPC")
@Description("Moves a quest NPC to another of its positions for players only (the other players keep seeing it where it is). " +
    "The mode is 'instant' (the default: it just appears at the new position), 'constant' (it walks to the new position, in a straight line, at a " +
    "constant speed), 'wait' (like constant, but it stops and waits when the player is too far) or 'follow' (it adapts its speed to the player). " +
    "The speed goes from 0 (very slow) to 4 (fast), 2 by default. The walk is played by a copy of the NPC that only the player sees. " +
    "The effect does not wait for the walk to end. Positions start at 1; nothing happens when the NPC has no entity at that position.")
@Examples({
    "move quest npc \"maire\" to position 2 for player",
    "move quest npc \"maire\" to position 3 for player using mode \"follow\" at speed 3"
})
@Since("1.6.0")
public class EffMoveNpc extends Effect {

    private Expression<QuestNpc> npcExpr;
    private Expression<Number> positionExpr;
    private Expression<Player> playersExpr;
    private @Nullable Expression<String> modeExpr;
    private @Nullable Expression<Number> speedExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffMoveNpc.class)
                .addPattern("move %questnpc% to position %number% for %players% [using mode %-string%] [at speed %-number%]")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        npcExpr = (Expression<QuestNpc>) exprs[0];
        positionExpr = (Expression<Number>) exprs[1];
        playersExpr = (Expression<Player>) exprs[2];
        modeExpr = (Expression<String>) exprs[3];
        speedExpr = (Expression<Number>) exprs[4];
        return true;
    }

    @Override
    protected void execute(Event event) {
        QuestNpc npc = npcExpr.getSingle(event);
        Number position = positionExpr.getSingle(event);
        if (npc == null || position == null) return;
        String modeName = modeExpr == null ? null : modeExpr.getSingle(event);
        NpcMover.Mode mode = NpcMover.Mode.parse(modeName);
        if (modeName != null && mode == NpcMover.Mode.INSTANT && !modeName.equalsIgnoreCase("instant")) {
            Skript.warning("Unknown quest npc move mode '" + modeName + "': use instant, constant, wait or follow.");
        }
        NpcMover.Options options = new NpcMover.Options();
        Number speed = speedExpr == null ? null : speedExpr.getSingle(event);
        if (speed != null) options.speed = speed.intValue();
        for (Player player : playersExpr.getArray(event)) NpcMover.start(player, npc, position.intValue(), mode, options, null);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "move " + npcExpr.toString(event, debug) + " to position " + positionExpr.toString(event, debug) + " for " + playersExpr.toString(event, debug)
            + (modeExpr == null ? "" : " using mode " + modeExpr.toString(event, debug)) + (speedExpr == null ? "" : " at speed " + speedExpr.toString(event, debug));
    }
}
