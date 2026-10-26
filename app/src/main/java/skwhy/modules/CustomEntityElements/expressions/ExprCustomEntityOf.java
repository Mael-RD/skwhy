package skwhy.modules.CustomEntityElements.expressions;

import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

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

@Name("[Custom Entity] Custom Entity Of")
@Description("Gets the custom entity of an interaction entity that holds a custom entity id. Not set for any other entity.")
@Examples({
    "set {_custom} to custom entity of target entity"
})
@Since("1.6.0")
public class ExprCustomEntityOf extends SimpleExpression<CustomEntity> {

    private Expression<Entity> entityExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprCustomEntityOf.class, CustomEntity.class)
                .addPattern("custom entity of %entity%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        entityExpr = (Expression<Entity>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable CustomEntity[] get(Event event) {
        CustomEntityManager manager = CustomEntityManager.get();
        Entity entity = entityExpr.getSingle(event);
        if (manager == null || entity == null) return null;
        CustomEntity custom = manager.handleOf(entity);
        return custom == null ? null : new CustomEntity[]{ custom };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends CustomEntity> getReturnType() { return CustomEntity.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "custom entity of " + entityExpr.toString(event, debug);
    }
}
