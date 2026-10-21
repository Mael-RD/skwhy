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
import skwhy.quest.npc.CompassService;

@Name("[Quest] Enable / Disable / Refresh Compass")
@Description("Shows or hides the quest compass of players (see 'compass of'), or recomputes its targets at once " +
    "(they are normally recomputed by themselves when the progress or the selected quest changes).")
@Examples({
    "enable compass for player",
    "disable compass for all players",
    "refresh compass of player"
})
@Since("1.6.0")
public class EffCompass extends Effect {

    private Expression<Player> playersExpr;
    private int mode; // 0 : activer, 1 : désactiver, 2 : rafraîchir

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffCompass.class)
                .addPattern("enable compass for %players%")
                .addPattern("disable compass for %players%")
                .addPattern("refresh compass of %players%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        playersExpr = (Expression<Player>) exprs[0];
        mode = matchedPattern;
        return true;
    }

    @Override
    protected void execute(Event event) {
        for (Player player : playersExpr.getArray(event)) {
            if (mode == 2) CompassService.invalidate(player.getUniqueId());
            else CompassService.setEnabled(player, mode == 0);
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return switch (mode) {
            case 0 -> "enable compass for " + playersExpr.toString(event, debug);
            case 1 -> "disable compass for " + playersExpr.toString(event, debug);
            default -> "refresh compass of " + playersExpr.toString(event, debug);
        };
    }
}
