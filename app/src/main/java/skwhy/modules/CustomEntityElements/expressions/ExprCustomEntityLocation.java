package skwhy.modules.CustomEntityElements.expressions;

import org.bukkit.Location;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import skwhy.customentity.CustomEntity;
import skwhy.customentity.CustomEntityManager;

@Name("[Custom Entity] Location Of Custom Entity")
@Description("The location of a custom entity. Setting it teleports the entity (a real one is teleported on the server, a fake one is moved for the players who see it). " +
    "Not set for a destroyed custom entity.")
@Examples({
    "set {_where} to location of custom entity {_pet}",
    "set location of custom entity {_pet} to location of player"
})
@Since("1.6.0")
public class ExprCustomEntityLocation extends SimpleExpression<Location> {

    private Expression<CustomEntity> customExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprCustomEntityLocation.class, Location.class)
                .addPattern("location of custom entity %customentity%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        customExpr = (Expression<CustomEntity>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Location[] get(Event event) {
        CustomEntityManager manager = CustomEntityManager.get();
        CustomEntity custom = customExpr.getSingle(event);
        if (manager == null || custom == null || custom.isDestroyed()) return null;
        Location location = manager.locationOf(custom);
        return location == null ? null : new Location[]{ location };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        return mode == ChangeMode.SET ? new Class<?>[]{ Location.class } : null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        CustomEntityManager manager = CustomEntityManager.get();
        CustomEntity custom = customExpr.getSingle(event);
        if (manager == null || custom == null || delta == null || delta.length == 0 || !(delta[0] instanceof Location to)) return;
        manager.teleport(custom, to);
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Location> getReturnType() { return Location.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "location of custom entity " + customExpr.toString(event, debug);
    }
}
