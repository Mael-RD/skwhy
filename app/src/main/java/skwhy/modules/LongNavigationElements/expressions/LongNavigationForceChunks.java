package skwhy.modules.LongNavigationElements.expressions;

import ch.njol.skript.classes.Changer.ChangeMode;
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

@Name("[Long Navigation] Force Chunk Loading")
@Description("Whether a long navigation may have chunks loaded for it (false by default). " +
    "False: no chunk is ever loaded for it. A walk whose next chunk is not loaded waits for it, and 'blocks of long " +
    "navigation' stops before the first unloaded chunk. " +
    "True: a walk that waits for terrain asks for the chunks it needs (loaded asynchronously, never generated), and " +
    "'blocks of long navigation' reads the part of the path that is in unloaded chunks from the region files, " +
    "without loading those chunks (so an entity or a player does not have to go through them first). " +
    "Changing it discards the path already computed for 'blocks of long navigation'.")
@Examples({
    "set {_longnav} to a new long navigation with entity target entity speed 0.2",
    "set force chunk loading of long navigation {_longnav} to true",
    "set {_forced} to force chunk loading of long navigation {_longnav}",
    "reset force chunk loading of long navigation {_longnav}"
})
@Since("1.5.0")
public class LongNavigationForceChunks extends SimpleExpression<Boolean> {

    private Expression<LongNavigation> navigationExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.navigationExpr = (Expression<LongNavigation>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Boolean[] get(Event event) {
        LongNavigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return null;
        return new Boolean[]{ navigation.isForceChunkLoading() };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ Boolean.class };
        if (mode == ChangeMode.RESET) return new Class<?>[0];
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        LongNavigation navigation = navigationExpr.getSingle(event);
        if (navigation == null) return;
        if (mode == ChangeMode.RESET) {
            navigation.setForceChunkLoading(false);
        } else if (mode == ChangeMode.SET && delta != null && delta.length > 0 && delta[0] instanceof Boolean b) {
            navigation.setForceChunkLoading(b);
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Boolean> getReturnType() { return Boolean.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "force chunk loading of " + navigationExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(LongNavigationForceChunks.class, Boolean.class)
                .addPattern("force chunk loading of long navigation %longnavigation%")
                .build()
        );
    }
}
