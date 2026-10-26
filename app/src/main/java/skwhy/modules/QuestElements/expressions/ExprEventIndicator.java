package skwhy.modules.QuestElements.expressions;

import ch.njol.skript.Skript;
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
import skwhy.modules.QuestElements.events.QuestIndicatorUpdateEvent;
import skwhy.quest.model.QuestIndicator;

@Name("[Quest] Displayed Quest Indicator")
@Description("In a 'quest indicator update' event, the marker that is about to be shown above the NPC. Can be set to another marker " +
    "(or 'none' to hide it) before the event ends.")
@Examples({
    "on quest indicator update:",
    "\tif displayed quest indicator is new:",
    "\t\tset displayed quest indicator to main"
})
@Since("1.6.0")
public class ExprEventIndicator extends SimpleExpression<QuestIndicator> {

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        if (!getParser().isCurrentEvent(QuestIndicatorUpdateEvent.class)) {
            Skript.error("'displayed quest indicator' can only be used in a 'quest indicator update' event.");
            return false;
        }
        return true;
    }

    @Override
    protected QuestIndicator @Nullable [] get(Event event) {
        if (!(event instanceof QuestIndicatorUpdateEvent update)) return null;
        return new QuestIndicator[]{ update.getIndicator() };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        return mode == ChangeMode.SET ? new Class<?>[]{ QuestIndicator.class } : null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (event instanceof QuestIndicatorUpdateEvent update && delta != null && delta.length > 0 && delta[0] instanceof QuestIndicator indicator) {
            update.setIndicator(indicator);
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends QuestIndicator> getReturnType() { return QuestIndicator.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "displayed quest indicator";
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprEventIndicator.class, QuestIndicator.class)
                .addPattern("(displayed|shown) quest indicator")
                .build()
        );
    }
}
