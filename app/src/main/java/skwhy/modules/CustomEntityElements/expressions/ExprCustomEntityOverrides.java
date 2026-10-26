package skwhy.modules.CustomEntityElements.expressions;

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

@Name("[Custom Entity] Custom Entity Overrides")
@Description("The names of the parameters that a custom entity overrides (see 'custom entity parameter'), in the order they were set. " +
    "Deleting or resetting it removes every override of the entity, which gives back all the values of its definition.")
@Examples({
    "send custom entity overrides of {_pet} to player",
    "if custom entity overrides of {_pet} contains \"scale\":",
    "\treset custom entity overrides of {_pet}"
})
@Since("1.6.0")
public class ExprCustomEntityOverrides extends SimpleExpression<String> {

    private Expression<CustomEntity> entityExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprCustomEntityOverrides.class, String.class)
                .addPattern("custom entity overrides of %customentity%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        entityExpr = (Expression<CustomEntity>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable String[] get(Event event) {
        CustomEntity entity = entityExpr.getSingle(event);
        if (entity == null) return null;
        return entity.overrides().keySet().toArray(new String[0]);
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        return mode == ChangeMode.DELETE || mode == ChangeMode.RESET ? new Class<?>[0] : null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        CustomEntityManager manager = CustomEntityManager.get();
        CustomEntity entity = entityExpr.getSingle(event);
        if (manager != null && entity != null) manager.clearParams(entity);
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "custom entity overrides of " + entityExpr.toString(event, debug);
    }
}
