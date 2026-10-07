package skwhy.modules.LongNavigationElements.expressions;

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
import skwhy.chunkpath.longnav.LongNavigation;

@Name("[Long Navigation] Entity")
@Description("Returns the real Bukkit entity attached to a long navigation object. " +
    "Returns nothing if the long navigation was created from a numeric ID rather than a real entity.")
@Examples({
    "set {_longnav} to a new long navigation with entity target entity speed 0.2",
    "set {_entity} to entity of long navigation {_longnav}",
    "set {_entity} to long navigation {_longnav}'s entity"
})
@Since("1.5.0")
public class LongNavigationEntity extends SimpleExpression<Entity> {

    private Expression<LongNavigation> navigationExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.navigationExpr = (Expression<LongNavigation>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Entity[] get(Event event) {
        LongNavigation navigation = navigationExpr.getSingle(event);
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
            SyntaxInfo.Expression.builder(LongNavigationEntity.class, Entity.class)
                .addPattern("entity of long navigation %longnavigation%")
                .addPattern("long navigation %longnavigation%'s entity")
                .build()
        );
    }
}
