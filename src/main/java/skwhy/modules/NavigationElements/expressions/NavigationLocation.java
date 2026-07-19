package skwhy.modules.NavigationElements.expressions;

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
import skwhy.pathfinder.Navigation;

@Name("[Navigation] Location / Destination")
@Description("Gets or sets the current location or the movement destination of a navigation object.")
@Examples({
    "set {_navigation} to a new fake navigation with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player type \"WALK\"",
    "",
    "# Read the current location",
    "set {_loc} to location of navigation {_navigation}",
    "",
    "# Teleport the fake entity to a new position",
    "set location of navigation {_navigation} to location of player",
    "",
    "# Read the current destination",
    "set {_dest} to destination of navigation {_navigation}",
    "",
    "# Update the movement target",
    "set destination of navigation {_navigation} to location(100, 64, 200, world \"world\")"
})
@Since("1.2.0")
public class NavigationLocation extends SimpleExpression<Location> {

    private int matchedPattern;
    private Expression<Navigation> navigationExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.matchedPattern = matchedPattern;
        this.navigationExpr = (Expression<Navigation>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Location[] get(Event event) {
        Navigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return null;
        Location value = (matchedPattern == 0) ? navigation.getLocation() : navigation.getDestination();
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
        Navigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return;

        if (matchedPattern == 0) {
            navigation.setLocation(location);
        } else {
            navigation.setDestination(location);
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Location> getReturnType() { return Location.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return switch (matchedPattern) {
            case 0 -> "path location of " + navigationExpr.toString(event, debug);
            default -> "path destination of " + navigationExpr.toString(event, debug);
        };
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(NavigationLocation.class, Location.class)
                .addPattern("[fake] location of navigation %navigation%")
                .addPattern("[fake] destination of navigation %navigation%")
                .build()
        );
    }
}
