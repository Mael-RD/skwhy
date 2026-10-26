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
import skwhy.quest.QuestEngine;
import skwhy.quest.model.Quest;

@Name("[Quest] Select Quest")
@Description("Makes players follow a quest: the compass shows its destinations. Same as setting 'selected quest of'.")
@Examples({
    "select quest \"chasseur\" for player"
})
@Since("1.6.0")
public class EffSelectQuest extends Effect {

    private Expression<Quest> questExpr;
    private Expression<Player> playersExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffSelectQuest.class)
                .addPattern("select %quest% for %players%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        questExpr = (Expression<Quest>) exprs[0];
        playersExpr = (Expression<Player>) exprs[1];
        return true;
    }

    @Override
    protected void execute(Event event) {
        Quest quest = questExpr.getSingle(event);
        if (quest == null) return;
        for (Player player : playersExpr.getArray(event)) QuestEngine.select(player, quest);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "select " + questExpr.toString(event, debug) + " for " + playersExpr.toString(event, debug);
    }
}
