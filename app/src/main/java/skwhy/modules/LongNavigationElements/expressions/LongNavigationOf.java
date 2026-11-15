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

import java.util.List;

@Name("[Long Navigation] Of Entity")
@Description("The long navigation of an entity: a real entity, or a fake entity by its id (a navigation created with " +
    "'with id'). Returns nothing when there is none. The security keeps one navigation per entity (see 'a new long " +
    "navigation'), so the answer is unique; when 'long_navigation.allow_multiple' is true and the entity has several, " +
    "'long navigation of' logs an error in the console (the script goes on) and returns the first created one, and " +
    "'all long navigations of' returns every one. Use it to pause (set 'pause ticks of long navigation' to -1), resume " +
    "(0), change the end or destroy the navigation of an entity from another script or from an event.")
@Examples({
    "set {_nav} to long navigation of {_villager}",
    "if long navigation of {_villager} is set:",
    "\tset pause ticks of long navigation (long navigation of {_villager}) to -1",
    "",
    "# a fake entity, by its id",
    "set pause ticks of long navigation (long navigation of entity id 12345) to 0"
})
@Since("1.6.1")
public class LongNavigationOf extends SimpleExpression<LongNavigation> {

    private Expression<Entity> entityExpr;
    private Expression<Number> idExpr;
    private boolean all;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.all = matchedPattern >= 2;
        if (matchedPattern % 2 == 0) entityExpr = (Expression<Entity>) exprs[0];
        else idExpr = (Expression<Number>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable LongNavigation[] get(Event event) {
        int id;
        if (entityExpr != null) {
            Entity entity = entityExpr.getSingle(event);
            if (entity == null) return null;
            id = entity.getEntityId();
        } else {
            Number number = idExpr.getSingle(event);
            if (number == null) return null;
            id = number.intValue();
        }
        if (all) {
            List<LongNavigation> list = LongNavigation.allOf(id);
            return list.isEmpty() ? null : list.toArray(new LongNavigation[0]);
        }
        LongNavigation navigation = LongNavigation.of(id);
        return navigation == null ? null : new LongNavigation[]{ navigation };
    }

    @Override
    public boolean isSingle() { return !all; }

    @Override
    public Class<? extends LongNavigation> getReturnType() { return LongNavigation.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        String target = entityExpr != null ? entityExpr.toString(event, debug) : "entity id " + idExpr.toString(event, debug);
        return (all ? "all long navigations of " : "long navigation of ") + target;
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(LongNavigationOf.class, LongNavigation.class)
                .addPattern("long navigation of %entity%")
                .addPattern("long navigation of entity id %number%")
                .addPattern("all long navigations of %entity%")
                .addPattern("all long navigations of entity id %number%")
                .build()
        );
    }
}
