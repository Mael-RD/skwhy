package skwhy.modules.LongNavigationElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.chunkpath.longnav.LongNavigation;

@Name("[Long Navigation] Status")
@Description("Returns the current status of a long navigation, as lowercase text: \"idle\" (no route yet), " +
    "\"following\" (walking toward the current waypoint), \"stuck\" (local pathfinding failed, retrying " +
    "periodically), \"waiting_for_chunk\" (next waypoint's chunk isn't loaded, waiting per the catch-up " +
    "strategy), \"arrived\", or \"failed_no_path\" (the chunk-gate route couldn't be computed — see the console " +
    "for why). Read-only.")
@Examples({
    "if status of long navigation {_longnav} is \"arrived\":",
    "\tbroadcast \"reached the destination!\"",
    "",
    "if status of long navigation {_longnav} is \"failed_no_path\":",
    "\tbroadcast \"no route found, check the console\""
})
@Since("1.5.0")
public class LongNavigationStatus extends SimpleExpression<String> {

    private Expression<LongNavigation> navigationExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.navigationExpr = (Expression<LongNavigation>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable String[] get(Event event) {
        LongNavigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return null;
        return new String[]{ navigation.getStatus().name().toLowerCase() };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "status of " + navigationExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(LongNavigationStatus.class, String.class)
                .addPattern("status of long navigation %longnavigation%")
                .build()
        );
    }
}
