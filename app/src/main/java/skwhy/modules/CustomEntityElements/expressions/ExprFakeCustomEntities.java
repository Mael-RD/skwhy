package skwhy.modules.CustomEntityElements.expressions;

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

@Name("[Custom Entity] All Fake Custom Entities")
@Description("The fake custom entities that exist. They are not saved: they disappear when the server stops.")
@Examples({
    "destroy all fake custom entities"
})
@Since("1.6.0")
public class ExprFakeCustomEntities extends SimpleExpression<CustomEntity> {

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprFakeCustomEntities.class, CustomEntity.class)
                .addPattern("all fake custom entities")
                .build()
        );
    }

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        return true;
    }

    @Override
    protected @Nullable CustomEntity[] get(Event event) {
        CustomEntityManager manager = CustomEntityManager.get();
        return manager == null ? new CustomEntity[0] : manager.fakes().toArray(new CustomEntity[0]);
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends CustomEntity> getReturnType() { return CustomEntity.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "all fake custom entities";
    }
}
