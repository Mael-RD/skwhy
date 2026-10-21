package skwhy.modules.QuestElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
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

@Name("[Quest] Quest NPC Indicator")
@Description("The marker a quest NPC would show to a player, computed from the conditions of its script (the 'indicator' of the first " +
    "action of the 'main' list whose conditions are met): new, main, secondary, region, info or none. " +
    "Only scripted NPCs have markers.")
@Examples({
    "if indicator of quest npc \"maire\" for player is new:",
    "\tsend \"Le maire a une quête pour toi\""
})
@Since("1.6.0")
public class ExprNpcIndicator extends SimpleExpression<QuestIndicator> {

    private Expression<QuestNpc> npcExpr;
    private Expression<Player> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        npcExpr = (Expression<QuestNpc>) exprs[0];
        playerExpr = (Expression<Player>) exprs[1];
        return true;
    }

    @Override
    protected QuestIndicator @Nullable [] get(Event event) {
        QuestNpc npc = npcExpr.getSingle(event);
        Player player = playerExpr.getSingle(event);
        if (npc == null || player == null) return null;
        return new QuestIndicator[]{ IndicatorService.compute(player, npc, null) };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends QuestIndicator> getReturnType() { return QuestIndicator.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "indicator of " + npcExpr.toString(event, debug) + " for " + playerExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprNpcIndicator.class, QuestIndicator.class)
                .addPattern("indicator of %questnpc% for %player%")
                .build()
        );
    }
}
