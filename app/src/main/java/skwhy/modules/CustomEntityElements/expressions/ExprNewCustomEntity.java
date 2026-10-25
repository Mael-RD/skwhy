package skwhy.modules.CustomEntityElements.expressions;

import org.bukkit.Location;
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

@Name("[Custom Entity] New Custom Entity")
@Description("Creates a custom entity from the id of a definition of the custom-entities folder (see 'reload custom entities'). " +
    "A real custom entity is an interaction entity of the server whose NBT only holds the id (it stays after a restart); " +
    "a fake custom entity does not exist on the server, only for the players near it (view_distance in the config) through packets, and is lost on restart. " +
    "In both cases the look is sent by packet from the definition. Nothing is created (the result is not set) when the id is not defined.")
@Examples({
    "set {_pet} to a new fake custom entity \"example_wolf\" at location of player",
    "set {_marchand} to a new real custom entity \"example_mannequin\" at location of player"
})
@Since("1.6.0")
public class ExprNewCustomEntity extends SimpleExpression<CustomEntity> {

    private boolean fake;
    private Expression<String> idExpr;
    private Expression<Location> locationExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprNewCustomEntity.class, CustomEntity.class)
                .addPattern("[a] [new] real custom entity %string% at %location%")
                .addPattern("[a] [new] fake custom entity %string% at %location%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        fake = matchedPattern == 1;
        idExpr = (Expression<String>) exprs[0];
        locationExpr = (Expression<Location>) exprs[1];
        return true;
    }

    @Override
    protected @Nullable CustomEntity[] get(Event event) {
        CustomEntityManager manager = CustomEntityManager.get();
        String id = idExpr.getSingle(event);
        Location location = locationExpr.getSingle(event);
        if (manager == null || id == null || location == null) return null;
        CustomEntity entity = fake ? manager.spawnFake(id, location) : manager.spawnReal(id, location);
        return entity == null ? null : new CustomEntity[]{ entity };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends CustomEntity> getReturnType() { return CustomEntity.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "new " + (fake ? "fake" : "real") + " custom entity " + idExpr.toString(event, debug) + " at " + locationExpr.toString(event, debug);
    }
}
