package skwhy.modules.QuestElements.expressions;

import java.util.ArrayList;
import java.util.List;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.OfflinePlayer;
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

@Name("[Quest] Dialogue State")
@Description("Where a player is in the dialogues of a quest NPC: the name of the current dialogue of each 'dialogue' action of the NPC script " +
    "(the player has talked to the NPC: a dialogue without saved state starts at 'start'). " +
    "Set it to jump every dialogue action that has a dialogue of this name to it. Delete or reset forgets the progress: the NPC starts over.")
@Examples({
    "send \"%dialogue state of quest npc \"maire\" for player%\"",
    "set dialogue state of quest npc \"maire\" for player to \"suite\"",
    "reset dialogue state of quest npc \"maire\" for player"
})
@Since("1.6.0")
public class ExprDialogueState extends SimpleExpression<String> {

    private Expression<QuestNpc> npcExpr;
    private Expression<OfflinePlayer> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        npcExpr = (Expression<QuestNpc>) exprs[0];
        playerExpr = (Expression<OfflinePlayer>) exprs[1];
        return true;
    }

    private static List<ScriptNode> dialogueActions(QuestNpc npc) {
        List<ScriptNode> out = new ArrayList<>();
        for (ScriptNode node : npc.script().all().values()) {
            if ("dialogue".equals(node.action())) out.add(node);
        }
        return out;
    }

    @Override
    protected String @Nullable [] get(Event event) {
        QuestNpc npc = npcExpr.getSingle(event);
        OfflinePlayer player = playerExpr.getSingle(event);
        if (npc == null || player == null) return null;
        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        List<String> out = new ArrayList<>();
        for (ScriptNode node : dialogueActions(npc)) {
            String state = data.dialogues.get(node.id());
            if (state != null) out.add(state);
        }
        return out.toArray(new String[0]);
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ String.class };
        if (mode == ChangeMode.RESET || mode == ChangeMode.DELETE) return new Class<?>[0];
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        QuestNpc npc = npcExpr.getSingle(event);
        OfflinePlayer player = playerExpr.getSingle(event);
        if (npc == null || player == null) return;
        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        for (ScriptNode node : dialogueActions(npc)) {
            if (mode == ChangeMode.SET && delta != null && delta.length > 0) {
                String state = String.valueOf(delta[0]);
                if (Tree.map(node.data(), "dialogues").containsKey(state)) data.dialogues.put(node.id(), state);
            } else {
                data.dialogues.remove(node.id());
            }
        }
        data.markDirty();
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "dialogue state of " + npcExpr.toString(event, debug) + " for " + playerExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprDialogueState.class, String.class)
                .addPattern("dialogue state of %questnpc% for %offlineplayer%")
                .build()
        );
    }
}
