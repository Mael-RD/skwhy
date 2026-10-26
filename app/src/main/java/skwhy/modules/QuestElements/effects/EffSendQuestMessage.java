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
import skwhy.quest.npc.DialogueService;

@Name("[Quest] Send Quest Message")
@Description("Sends a message said by a quest NPC to players, formatted like the messages of the NPC dialogues: ' [Name] text', " +
    "with ' ??? ' as the name until the player knows it (a message that contains the name of the NPC makes the player learn it). " +
    "The text accepts the usual color tags and the quest tags %nl%, %player%, %genre::masculine::feminine% and %variable::quest::name%: " +
    "in a Skript string a literal percent is written twice, so write them as %%player%%.")
@Examples({
    "send quest message \"Bonjour %%player%% !\" from quest npc \"maire\" to player"
})
@Since("1.6.0")
public class EffSendQuestMessage extends Effect {

    private Expression<String> textExpr;
    private Expression<QuestNpc> npcExpr;
    private Expression<Player> playersExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffSendQuestMessage.class)
                .addPattern("send quest message %string% from %questnpc% to %players%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        textExpr = (Expression<String>) exprs[0];
        npcExpr = (Expression<QuestNpc>) exprs[1];
        playersExpr = (Expression<Player>) exprs[2];
        return true;
    }

    @Override
    protected void execute(Event event) {
        String text = textExpr.getSingle(event);
        QuestNpc npc = npcExpr.getSingle(event);
        if (text == null || npc == null) return;
        for (Player player : playersExpr.getArray(event)) DialogueService.say(player, npc, text, null, null);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "send quest message " + textExpr.toString(event, debug) + " from " + npcExpr.toString(event, debug) + " to " + playersExpr.toString(event, debug);
    }
}
