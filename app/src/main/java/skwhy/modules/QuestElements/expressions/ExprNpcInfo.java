package skwhy.modules.QuestElements.expressions;

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
import skwhy.quest.model.QuestNpc;

@Name("[Quest] Quest NPC Info")
@Description("Properties of a quest NPC definition: its id, its name (with its color codes; the id when it has none), " +
    "its display name, its type (discour, marchand or scripté; nothing when it has none) and the number of positions it can stand at.")
@Examples({
    "send quest npc name of quest npc \"maire\"",
    "if quest npc type of {_npc} is \"marchand\":",
    "\tsend \"Un marchand\""
})
@Since("1.6.0")
public class ExprNpcInfo extends SimpleExpression<Object> {

    private static final String[] PROPERTIES = {"id", "name", "display name", "type", "position count"};

    private int property;
    private Expression<QuestNpc> npcExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        property = pr.mark;
        npcExpr = (Expression<QuestNpc>) exprs[0];
        return true;
    }

    @Override
    protected Object @Nullable [] get(Event event) {
        QuestNpc npc = npcExpr.getSingle(event);
        if (npc == null) return null;
        return switch (property) {
            case 0 -> new Object[]{ npc.id() };
            case 1, 2 -> new Object[]{ npc.displayName() };
            case 3 -> npc.type() == null ? null : new Object[]{ npc.type() };
            default -> new Object[]{ npc.positions().size() };
        };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<?> getReturnType() { return Object.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "quest npc " + PROPERTIES[property] + " of " + npcExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprNpcInfo.class, Object.class)
                .addPattern("quest npc (0:id|1:name|2:display name|3:type|4:position count) of %questnpc%")
                .build()
        );
    }
}
