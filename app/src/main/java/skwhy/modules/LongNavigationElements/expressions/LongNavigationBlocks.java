package skwhy.modules.LongNavigationElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.chunkpath.longnav.LongNavigation;

import java.util.List;

@Name("[Long Navigation] Path Blocks")
@Description("The cells a long navigation goes through from where its entity stands now, as a list of locations (the middle of " +
    "the block the feet are in), nearest first: the next N of them, or all of them up to the end location. Nothing moves: " +
    "this is how the path of a player is obtained, because a long navigation made with a player never drives it (no " +
    "teleport, no gravity change), it only answers this expression; it works for any other long navigation too, even paused. " +
    "The end location and the path cost rules must be set (see 'end location of' and 'using rules'). " +
    "Only what is missing is computed at each call: the route between chunks is kept while the entity stays in the chunks it " +
    "goes through, the cells already passed are dropped, a position a little off the path is brought back onto it by one small " +
    "search, and asking for more cells only searches the part after the ones already known. " +
    "A part in a loaded chunk is searched like a real walk would. A part in an unloaded chunk is read from the region file " +
    "(the chunk is not loaded) when 'force chunk loading of long navigation' is true; when it is false, the list stops before " +
    "the first unloaded chunk. The list is empty when the entity has arrived, when there is no route or when the end location is unset.")
@Examples({
    "set {_rule} to a new path cost rule with cost 1",
    "set {_longnav} to a new long navigation with entity player using rules {_rule}",
    "set end location of long navigation {_longnav} to location(120.5, 70, 40.5, world \"world\")",
    "set force chunk loading of long navigation {_longnav} to true",
    "# the next 20 cells of the way, e.g. to show them to the player",
    "loop next 20 blocks of long navigation {_longnav}:",
    "\tbroadcast \"next cell: %loop-value%\"",
    "set {_all::*} to all blocks of long navigation {_longnav}"
})
@Since("1.5.0")
public class LongNavigationBlocks extends SimpleExpression<Location> {

    private Expression<Number> countExpr; // null = all of them
    private Expression<LongNavigation> navigationExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        if (matchedPattern == 0) {
            this.countExpr = (Expression<Number>) exprs[0];
            this.navigationExpr = (Expression<LongNavigation>) exprs[1];
        } else {
            this.navigationExpr = (Expression<LongNavigation>) exprs[0];
        }
        return true;
    }

    @Override
    protected @Nullable Location[] get(Event event) {
        LongNavigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return null;
        int limit = Integer.MAX_VALUE;
        if (countExpr != null) {
            Number count = countExpr.getSingle(event);
            if (count == null) return null;
            limit = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, count.longValue()));
        }
        List<Location> blocks = navigation.pathBlocks(limit);
        return blocks.toArray(new Location[0]);
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends Location> getReturnType() { return Location.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        String navigation = navigationExpr.toString(event, debug);
        return countExpr == null
            ? "all blocks of long navigation " + navigation
            : "next " + countExpr.toString(event, debug) + " blocks of long navigation " + navigation;
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(LongNavigationBlocks.class, Location.class)
                .addPattern("[the] next %integer% blocks of long navigation %longnavigation%")
                .addPattern("all [the] blocks of long navigation %longnavigation%")
                .build()
        );
    }
}
