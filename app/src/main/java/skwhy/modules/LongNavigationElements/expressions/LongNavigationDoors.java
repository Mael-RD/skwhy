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

@Name("[Long Navigation] Can Open Doors")
@Description("Whether the entity of a long navigation can open the doors on its way (true by default). " +
    "Only real doors are concerned: the entity opens a wooden (or copper) door just before walking through it " +
    "and closes it behind itself. Iron doors, fence gates and trapdoors are never opened. " +
    "When it cannot open doors, or meets a door it cannot open, the entity walks around it if there is a way. " +
    "The chunk path recorder always considers doors as passable.")
@Examples({
    "set {_longnav} to a new long navigation with entity target entity speed 0.2",
    "set can open doors of long navigation {_longnav} to false",
    "set {_can} to can open doors of long navigation {_longnav}",
    "reset can open doors of long navigation {_longnav}"
})
@Since("1.5.0")
public class LongNavigationDoors extends SimpleExpression<Boolean> {

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
        return new Boolean[]{ navigation.canOpenDoors() };
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
            navigation.setCanOpenDoors(true);
        } else if (mode == ChangeMode.SET && delta != null && delta.length > 0 && delta[0] instanceof Boolean b) {
            navigation.setCanOpenDoors(b);
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Boolean> getReturnType() { return Boolean.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "can open doors of " + navigationExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(LongNavigationDoors.class, Boolean.class)
                .addPattern("can open doors of long navigation %longnavigation%")
                .build()
        );
    }
}
