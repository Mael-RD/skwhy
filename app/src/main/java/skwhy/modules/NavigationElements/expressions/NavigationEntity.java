package skwhy.modules.NavigationElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.pathfinder.Navigation;

@Name("[Navigation] Entity")
@Description("Returns the real Bukkit entity attached to a navigation object. " +
    "Returns nothing if the fake navigation was created from a numeric ID rather than a real entity.")
@Examples({
    "set {_navigation} to a new navigation with entity target entity hitbox vector(0.6, 1.8, 0.6) location location of player type \"WALK\"",
    "",
    "# Get the entity using the 'entity of' syntax",
    "set {_entity} to entity of navigation {_navigation}",
    "",
    "# Get the entity using the possessive syntax",
    "set {_entity} to navigation {_navigation}'s entity"
})
@Since("1.2.0")
public class NavigationEntity extends SimpleExpression<Entity> {

    private Expression<Navigation> navigationExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.navigationExpr = (Expression<Navigation>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Entity[] get(Event event) {
        Navigation navigation = navigationExpr.getSingle(event);
        if (navigation == null || !navigation.isRealEntity()) return null;
        Entity entity = navigation.getEntity();
        if (entity == null) return null;
        return new Entity[]{ entity };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Entity> getReturnType() { return Entity.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "entity of " + navigationExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(NavigationEntity.class, Entity.class)
                .addPattern("entity of navigation %navigation%")
                .addPattern("navigation %navigation%'s entity")
                .build()
        );
    }
}