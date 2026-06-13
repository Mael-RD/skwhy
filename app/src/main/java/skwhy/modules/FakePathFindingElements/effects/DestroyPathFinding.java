package skwhy.modules.FakePathFindingElements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.FakePathFinding;

@Name("Destroy Fake Pathfinding")
@Description("Destroys one or more fake pathfinding instances, removing them from the global tick registry. " +
    "Once destroyed, the pathfinding object stops moving and its tick() method will no longer be called. " +
    "Always destroy pathfinding instances when they are no longer needed to avoid memory leaks.")
@Examples({
    "set {_fake} to a new fake pathfinding with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player type \"WALK\"",
    "",
    "# Destroy a single instance",
    "destroy fake pathfinding {_fake}",
    "",
    "# Destroy multiple instances at once",
    "unregister fake pathfinding {_fakes::*}"
})
@Since("1.2.0")
public class DestroyPathFinding extends Effect {

    private Expression<FakePathFinding> fakesExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        fakesExpr = (Expression<FakePathFinding>) exprs[0];
        return true;
    }

    @Override
    protected void execute(Event event) {
        FakePathFinding[] fakes = fakesExpr.getAll(event);
        if (fakes == null) return;
        for (FakePathFinding fake : fakes) {
            fake.unregister();
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "destroy fake pathfinding " + fakesExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(DestroyPathFinding.class)
                .addPattern("(destroy|unregister) [fake] pathfinding %fakepathfindings%")
                .build()
        );
    }
}