package skwhy.modules.QuestElements.expressions;

import java.util.ArrayList;
import java.util.List;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.npc.CompassService;

@Name("[Quest] Compass Targets")
@Description("The locations the compass of a player points at: the destinations of the current step of the quest he follows " +
    "(NPC destinations are the position of the NPC as the player sees it; search zones give their center). " +
    "Use it to draw your own compass or markers.")
@Examples({
    "loop compass targets of player:",
    "\tdraw 5 happy villager particles at loop-value for player"
})
@Since("1.6.0")
public class ExprCompassTargets extends SimpleExpression<Location> {

    private Expression<Player> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        playerExpr = (Expression<Player>) exprs[0];
        return true;
    }

    @Override
    protected Location @Nullable [] get(Event event) {
        Player player = playerExpr.getSingle(event);
        if (player == null) return null;
        List<Location> out = new ArrayList<>();
        for (CompassService.Target target : CompassService.targets(player)) {
            World world = Bukkit.getWorld(target.world() == null ? "" : target.world());
            if (world != null) out.add(new Location(world, target.x(), target.y(), target.z()));
        }
        return out.toArray(new Location[0]);
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends Location> getReturnType() { return Location.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "compass targets of " + playerExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprCompassTargets.class, Location.class)
                .addPattern("compass targets of %player%")
                .build()
        );
    }
}
