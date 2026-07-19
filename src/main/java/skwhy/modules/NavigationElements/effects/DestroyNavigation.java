package skwhy.modules.NavigationElements.effects;

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
import skwhy.pathfinder.Navigation;

@Name("[Navigation] Destroy Navigation")
@Description("Destroys one or more navigation instances, removing them from the global tick registry. " +
    "Once destroyed, the navigating object stops moving and its tick() method will no longer be called. " +
    "Always destroy navigation instances when they are no longer needed to avoid memory leaks.")
@Examples({
    "set {_fake} to a new fake navigation with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player type \"WALK\"",
    "",
    "# Destroy a single instance",
    "destroy fake navigation {_fake}",
    "",
    "# Destroy multiple instances at once",
    "unregister fake navigation {_fakes::*}"
})
@Since("1.2.0")
public class DestroyNavigation extends Effect {

    private Expression<Navigation> fakesExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        fakesExpr = (Expression<Navigation>) exprs[0];
        return true;
    }

    @Override
    protected void execute(Event event) {
        Navigation[] fakes = fakesExpr.getAll(event);
        if (fakes == null) return;
        for (Navigation fake : fakes) {
            fake.unregister();
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "destroy fake navigation " + fakesExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(DestroyNavigation.class)
                .addPattern("(destroy|unregister) [fake] navigation %navigations%")
                .build()
        );
    }
}