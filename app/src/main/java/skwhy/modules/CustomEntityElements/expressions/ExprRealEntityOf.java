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

@Name("[Custom Entity] Real Entity Of")
@Description("Gets the interaction entity of the server behind a real custom entity. Not set for a fake custom entity (it does not exist on the server) or a destroyed one.")
@Examples({
    "set {_interaction} to real entity of {_custom}"
})
@Since("1.6.0")
public class ExprRealEntityOf extends SimpleExpression<Entity> {

    private Expression<CustomEntity> customExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprRealEntityOf.class, Entity.class)
                .addPattern("real entity of %customentity%")
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
    protected @Nullable Entity[] get(Event event) {
        CustomEntityManager manager = CustomEntityManager.get();
        CustomEntity custom = customExpr.getSingle(event);
        if (manager == null || custom == null) return null;
        Entity real = manager.realEntity(custom);
        return real == null ? null : new Entity[]{ real };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Entity> getReturnType() { return Entity.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "real entity of " + customExpr.toString(event, debug);
    }
}
