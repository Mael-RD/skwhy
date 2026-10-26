package skwhy.modules.QuestElements.conditions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.docs.Origin;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.npc.CompassService;

@Name("[Quest] Has Compass Enabled")
@Description("Checks if the quest compass is shown to players.")
@Examples({
    "if player has the compass enabled:",
    "\tsend \"Ta boussole est affichée\""
})
@Since("1.6.0")
public class CondCompassEnabled extends Condition {

    private Expression<Player> playersExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.CONDITION,
            SyntaxInfo.builder(CondCompassEnabled.class)
                .origin(Origin.of(addon))
                .addPattern("%players% (has|have) [the] compass enabled")
                .addPattern("%players% (has|have)(n't| not) [the] compass enabled")
                .addPattern("%players% (do|does)(n't| not) have [the] compass enabled")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        playersExpr = (Expression<Player>) exprs[0];
        setNegated(matchedPattern >= 1);
        return true;
    }

    @Override
    public boolean check(Event event) {
        return playersExpr.check(event, player -> CompassService.isEnabled(player.getUniqueId()), isNegated());
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return playersExpr.toString(event, debug) + (isNegated() ? " hasn't" : " has") + " compass enabled";
    }
}
