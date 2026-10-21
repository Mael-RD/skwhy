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
import skwhy.quest.QuestRegistry;
import skwhy.quest.model.Quest;

@Name("[Quest] Quest")
@Description("A loaded quest, by its id (the name of its file without extension), or every loaded quest sorted by priority. " +
    "Nothing is returned when no quest has this id.")
@Examples({
    "set {_quest} to quest \"chasseur\"",
    "loop all loaded quests:",
    "\tsend \"%quest name of loop-value%\""
})
@Since("1.6.0")
public class ExprQuest extends SimpleExpression<Quest> {

    private @Nullable Expression<String> idExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        idExpr = matchedPattern == 0 ? (Expression<String>) exprs[0] : null;
        return true;
    }

    @Override
    protected Quest @Nullable [] get(Event event) {
        if (idExpr == null) return QuestRegistry.quests().toArray(new Quest[0]);
        String id = idExpr.getSingle(event);
        Quest quest = QuestRegistry.quest(id);
        return quest == null ? new Quest[0] : new Quest[]{ quest };
    }

    @Override
    public boolean isSingle() { return idExpr != null; }

    @Override
    public Class<? extends Quest> getReturnType() { return Quest.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return idExpr == null ? "all loaded quests" : "quest " + idExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprQuest.class, Quest.class)
                .addPattern("quest %string%")
                .addPattern("[all] loaded quests")
                .build()
        );
    }
}
