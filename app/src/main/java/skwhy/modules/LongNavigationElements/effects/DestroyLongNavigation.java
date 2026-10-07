package skwhy.modules.LongNavigationElements.effects;

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
import skwhy.chunkpath.longnav.LongNavigation;

@Name("[Long Navigation] Destroy Navigation")
@Description("Destroys one or more long navigation instances, removing them from the global tick registry. " +
    "Once destroyed, the navigating object stops moving and its tick() method will no longer be called. " +
    "Always destroy long navigation instances when they are no longer needed to avoid memory leaks.")
@Examples({
    "set {_longnav} to a new long navigation with entity target entity speed 0.2",
    "",
    "destroy long navigation {_longnav}",
    "unregister long navigation {_longnavs::*}"
})
@Since("1.5.0")
public class DestroyLongNavigation extends Effect {

    private Expression<LongNavigation> navigationsExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        navigationsExpr = (Expression<LongNavigation>) exprs[0];
        return true;
    }

    @Override
    protected void execute(Event event) {
        LongNavigation[] navigations = navigationsExpr.getAll(event);
        if (navigations == null) return;
        for (LongNavigation navigation : navigations) {
            navigation.unregister();
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "destroy long navigation " + navigationsExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(DestroyLongNavigation.class)
                .addPattern("(destroy|unregister) long navigation %longnavigations%")
                .build()
        );
    }
}
