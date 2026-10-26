package skwhy.modules.CustomEntityElements.effects;

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
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import skwhy.customentity.CustomEntity;
import skwhy.customentity.CustomEntityManager;

@Name("[Custom Entity] Make Custom Entity Walk")
@Description("Makes custom entities walk to a location with the vanilla pathfinding of their AI mob (the 'ai' parameter, see 'custom entity parameter'): " +
    "same navigation, same obstacles, doors and jumps as the mob would have. The speed is a multiplier of the walking speed of the mob (1 by default). " +
    "Real and fake custom entities are driven the same way. Nothing happens for an entity without AI, in a chunk that is not loaded, or when no path exists. " +
    "'stop walking' cancels the walk; the AI goals of the mob (wandering, chasing players...) go on.")
@Examples({
    "make custom entity {_pet} walk to location of player",
    "make custom entity {_pet} walk to {_target} at speed 1.5",
    "make custom entity {_pet} stop walking"
})
@Since("1.6.0")
public class EffCustomEntityWalk extends Effect {

    private boolean stop;
    private Expression<CustomEntity> entities;
    private Expression<Location> target;
    private Expression<Number> speed;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffCustomEntityWalk.class)
                .addPattern("make custom entit(y|ies) %customentities% walk to %location% [at speed %-number%]")
                .addPattern("make custom entit(y|ies) %customentities% stop walking")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        stop = matchedPattern == 1;
        entities = (Expression<CustomEntity>) exprs[0];
        if (!stop) {
            target = (Expression<Location>) exprs[1];
            speed = (Expression<Number>) exprs[2];
        }
        return true;
    }

    @Override
    protected void execute(Event event) {
        CustomEntityManager manager = CustomEntityManager.get();
        if (manager == null) return;
        if (stop) {
            for (CustomEntity entity : entities.getArray(event)) manager.stopWalking(entity);
            return;
        }
        Location to = target.getSingle(event);
        if (to == null) return;
        Number multiplier = speed == null ? null : speed.getSingle(event);
        double value = multiplier == null ? 1.0 : multiplier.doubleValue();
        for (CustomEntity entity : entities.getArray(event)) manager.walkTo(entity, to, value);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return stop
            ? "make custom entities " + entities.toString(event, debug) + " stop walking"
            : "make custom entities " + entities.toString(event, debug) + " walk to " + target.toString(event, debug)
                + (speed == null ? "" : " at speed " + speed.toString(event, debug));
    }
}
