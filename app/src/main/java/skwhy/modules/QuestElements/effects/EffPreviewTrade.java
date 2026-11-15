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
import skwhy.quest.QuestDefinitions;
import skwhy.quest.model.ScriptSet;
import skwhy.quest.npc.TradeService;
import skwhy.quest.script.ScriptContext;

@Name("[Quest] Preview Quest Trades")
@Description("Opens the trade window described by an NBT text (SNBT) of a trade table (a 'name' and a list of 'trades' with result, buy1, buy2, uses...), " +
    "as a quest NPC would. Used by trade editors to see the result; trades with a condition or an action are not run.")
@Examples("preview quest trades \"%{_nbt}%\" for player")
@Since("1.6.0")
public class EffPreviewTrade extends Effect {

    private Expression<String> textExpr;
    private Expression<Player> playersExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffPreviewTrade.class)
                .addPattern("preview quest trades %string% for %players%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        textExpr = (Expression<String>) exprs[0];
        playersExpr = (Expression<Player>) exprs[1];
        return true;
    }

    @Override
    protected void execute(Event event) {
        String text = textExpr.getSingle(event);
        var trade = text == null ? null : QuestDefinitions.parseTree(text);
        if (trade == null) return;
        for (Player player : playersExpr.getArray(event)) {
            TradeService.open(player, trade, new ScriptContext(player, new ScriptSet(java.util.Map.of()), null, null, null));
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "preview quest trades " + textExpr.toString(event, debug) + " for " + playersExpr.toString(event, debug);
    }
}
