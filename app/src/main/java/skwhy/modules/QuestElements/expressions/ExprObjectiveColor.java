package skwhy.modules.QuestElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.util.Color;
import ch.njol.skript.util.ColorRGB;
import ch.njol.util.Kleenean;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.npc.CompassService;

@Name("[Quest] Objective Color")
@Description("The color the compass uses for an objective (objectives are numbered from 1; 0 is the color of the destinations set on the step itself, without objective). " +
    "It comes from the list quests.compass_display.colors of the config and loops: with a list of 21 colors, objective 22 has the color of objective 1. " +
    "Use it to show in a menu the same color as the marker of the compass.")
@Examples({
    "set {_c} to quest objective color of objective 2",
    "set {_c} to quest objective color of objective 0",
    "loop 6 times:",
    "\tset {_c::%loop-number%} to quest objective color of objective loop-number"
})
@Since("1.6.0")
public class ExprObjectiveColor extends SimpleExpression<Color> {

    private Expression<Number> objectiveExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        objectiveExpr = (Expression<Number>) exprs[0];
        return true;
    }

    @Override
    protected Color @Nullable [] get(Event event) {
        Number objective = objectiveExpr.getSingle(event);
        if (objective == null) return null;
        TextColor color = CompassService.objectiveColor(objective.intValue());
        return new Color[]{ ColorRGB.fromRGB(color.red(), color.green(), color.blue()) };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Color> getReturnType() { return Color.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "quest objective color of objective " + objectiveExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprObjectiveColor.class, Color.class)
                .addPattern("quest objective color of objective %number%")
                .build()
        );
    }
}
