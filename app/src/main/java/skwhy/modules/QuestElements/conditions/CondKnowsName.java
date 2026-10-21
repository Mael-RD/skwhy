package skwhy.modules.QuestElements.conditions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.docs.Origin;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.data.QuestStore;
import skwhy.quest.model.QuestNpc;

@Name("[Quest] Knows NPC Name")
@Description("Checks if players know the name of a quest NPC (NPCs whose name is always visible are known by everybody).")
@Examples({
    "if player doesn't know name of quest npc \"maire\":",
    "\tsend \"Tu ne sais pas encore comment il s'appelle\""
})
@Since("1.6.0")
public class CondKnowsName extends Condition {

    private Expression<OfflinePlayer> playersExpr;
    private Expression<QuestNpc> npcExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.CONDITION,
            SyntaxInfo.builder(CondKnowsName.class)
                .origin(Origin.of(addon))
                .addPattern("%offlineplayers% (knows|know) [the] name of %questnpc%")
                .addPattern("%offlineplayers% (do|does)(n't| not) know [the] name of %questnpc%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        playersExpr = (Expression<OfflinePlayer>) exprs[0];
        npcExpr = (Expression<QuestNpc>) exprs[1];
        setNegated(matchedPattern == 1);
        return true;
    }

    @Override
    public boolean check(Event event) {
        QuestNpc npc = npcExpr.getSingle(event);
        if (npc == null) return isNegated();
        return playersExpr.check(event, player -> npc.nameVisible() || QuestStore.get(player.getUniqueId()).knownNames.contains(npc.id()), isNegated());
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return playersExpr.toString(event, debug) + (isNegated() ? " doesn't know" : " knows") + " name of " + npcExpr.toString(event, debug);
    }
}
