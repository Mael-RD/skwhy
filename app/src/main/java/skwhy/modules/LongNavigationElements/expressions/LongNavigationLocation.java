package skwhy.modules.LongNavigationElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.classes.Changer.ChangeMode;
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

@Name("[Long Navigation] Locations")
@Description("Gets or sets the start location, end location (destination), or current location of a long " +
    "navigation object. Setting the start or end location (re)computes the chunk-gate route; setting the " +
    "current location teleports the underlying entity (or virtual position) directly, like 'location of navigation'.")
@Examples({
    "set {_longnav} to a new long navigation with entity target entity speed 0.2",
    "set start location of long navigation {_longnav} to location of player",
    "set end location of long navigation {_longnav} to location(500, 64, 500, world \"world\")",
    "",
    "set {_here} to current location of long navigation {_longnav}",
    "set {_dest} to end location of long navigation {_longnav}"
})
@Since("1.5.0")
public class LongNavigationLocation extends SimpleExpression<Location> {

    private int matchedPattern;
    private Expression<LongNavigation> navigationExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.matchedPattern = matchedPattern;
        this.navigationExpr = (Expression<LongNavigation>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Location[] get(Event event) {
        LongNavigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return null;
        Location value = switch (matchedPattern) {
            case 0 -> navigation.getStart();
            case 1 -> navigation.getEnd();
            default -> navigation.getCurrentLocation();
        };
        return value != null ? new Location[]{ value } : null;
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ Location.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || delta.length == 0) return;
        if (!(delta[0] instanceof Location location)) return;
        LongNavigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return;

        switch (matchedPattern) {
            case 0 -> navigation.setStart(location);
            case 1 -> navigation.setEnd(location);
            default -> navigation.setCurrentLocation(location);
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Location> getReturnType() { return Location.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return switch (matchedPattern) {
            case 0 -> "start location of " + navigationExpr.toString(event, debug);
            case 1 -> "end location of " + navigationExpr.toString(event, debug);
            default -> "current location of " + navigationExpr.toString(event, debug);
        };
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(LongNavigationLocation.class, Location.class)
                .addPattern("start location of long navigation %longnavigation%")
                .addPattern("(end location|destination) of long navigation %longnavigation%")
                .addPattern("[current] location of long navigation %longnavigation%")
                .build()
        );
    }
}
