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
import skwhy.quest.Tree;
import skwhy.quest.data.PlayerQuestData;
import skwhy.quest.data.QuestStore;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.model.ScriptNode;
import skwhy.quest.script.ScriptContext;
import skwhy.quest.script.ScriptEngine;

@Name("[Quest] Start Quest Dialogue")
@Description("Makes a quest NPC say the dialogue of its script with this name to players: the first 'dialogue' action of the NPC that has a dialogue with this name " +
    "jumps to it and shows its first possible text. Nothing happens when the NPC has no dialogue with this name.")
@Examples({
    "start quest dialogue \"start\" of quest npc \"maire\" for player"
})
@Since("1.6.0")
public class EffStartDialogue extends Effect {

    private Expression<String> nameExpr;
    private Expression<QuestNpc> npcExpr;
    private Expression<Player> playersExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffStartDialogue.class)
                .addPattern("start quest dialogue %string% of %questnpc% for %players%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        nameExpr = (Expression<String>) exprs[0];
        npcExpr = (Expression<QuestNpc>) exprs[1];
        playersExpr = (Expression<Player>) exprs[2];
        return true;
    }

    @Override
    protected void execute(Event event) {
        String name = nameExpr.getSingle(event);
        QuestNpc npc = npcExpr.getSingle(event);
        if (name == null || npc == null) return;
        for (ScriptNode node : npc.script().all().values()) {
            if (!"dialogue".equals(node.action()) || !Tree.map(node.data(), "dialogues").containsKey(name)) continue;
            for (Player player : playersExpr.getArray(event)) {
                PlayerQuestData data = QuestStore.get(player.getUniqueId());
                data.dialogues.put(node.id(), name);
                data.markDirty();
                ScriptEngine.dialogue(ScriptContext.forNpc(player, npc, null), node.id(), false);
            }
            return;
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "start quest dialogue " + nameExpr.toString(event, debug) + " of " + npcExpr.toString(event, debug) + " for " + playersExpr.toString(event, debug);
    }
}
