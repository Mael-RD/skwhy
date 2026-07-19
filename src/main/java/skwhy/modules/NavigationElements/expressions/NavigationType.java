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
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.pathfinder.Navigation;

@Name("[Navigation] Pathfinding Type")
@Description("Gets or sets the movement type of a navigation object as a string. " +
    "Valid values are WALK, FLY, and SWIM. Invalid values are silently ignored and the type remains unchanged.")
@Examples({
    "set {_navigation} to a new navigation with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player type \"WALK\"",
    "",
    "# Read the current pathfinding type",
    "set {_type} to pathfinding type of navigation {_navigation}",
    "",
    "# Change to flying movement",
    "set pathfinding type of navigation {_navigation} to \"FLY\"",
    "",
    "# Change to swimming movement",
    "set pathfinding type of navigation {_navigation} to \"SWIM\""
})
@Since("1.2.0")
public class NavigationType extends SimpleExpression<String> {

    private Expression<Navigation> navigationExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.navigationExpr = (Expression<Navigation>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable String[] get(Event event) {
        Navigation navigation = navigationExpr.getSingle(event);
        if (navigation == null || navigation.getPathfindingType() == null) return null;
        return new String[]{ navigation.getPathfindingType().name() };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ String.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || delta.length == 0) return;
        Navigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return;

        Object raw = delta[0];
        if (raw instanceof String stringValue) {
            try {
                navigation.setPathfindingType(Navigation.PathfindingType.valueOf(stringValue.trim().toUpperCase().replace(' ', '_')));
            } catch (IllegalArgumentException ignored) {
            }
        } else if (raw instanceof Navigation.PathfindingType type) {
            navigation.setPathfindingType(type);
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "pathfinding type of " + navigationExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(NavigationType.class, String.class)
                .addPattern("pathfinding type of navigation %navigation%")
                .build()
        );
    }
}
