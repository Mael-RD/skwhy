package skwhy.modules.QuestElements.expressions;

import java.util.ArrayList;
import java.util.List;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.QuestEngine;
import skwhy.quest.QuestRegistry;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestStatus;

@Name("[Quest] Quests Of")
@Description("The loaded quests, sorted by priority, optionally limited to the ones a player has with a given status, " +
    "to a category (villes, quete_zelda...) and to a type (principale, secondaire, contrée). " +
    "The status filter needs a player ('unknown' returns the quests the player never unlocked).")
@Examples({
    "loop all quests of player with status started:",
    "\tsend \"%quest name of loop-value%\"",
    "set {_n} to size of quests of player with status finished in category \"villes\"",
    "set {_main::*} to quests with type \"principale\""
})
@Since("1.6.0")
public class ExprQuestsOf extends SimpleExpression<Quest> {

    private @Nullable Expression<OfflinePlayer> playerExpr;
    private @Nullable Expression<QuestStatus> statusExpr;
    private @Nullable Expression<String> categoryExpr;
    private @Nullable Expression<String> typeExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        playerExpr = (Expression<OfflinePlayer>) exprs[0];
        statusExpr = (Expression<QuestStatus>) exprs[1];
        categoryExpr = (Expression<String>) exprs[2];
        typeExpr = (Expression<String>) exprs[3];
        return true;
    }

    @Override
    protected Quest @Nullable [] get(Event event) {
        OfflinePlayer player = playerExpr == null ? null : playerExpr.getSingle(event);
        QuestStatus status = statusExpr == null ? null : statusExpr.getSingle(event);
        String category = categoryExpr == null ? null : categoryExpr.getSingle(event);
        String type = typeExpr == null ? null : typeExpr.getSingle(event);
        List<Quest> out = new ArrayList<>();
        for (Quest quest : QuestRegistry.quests()) {
            if (category != null && !category.equalsIgnoreCase(quest.category())) continue;
            if (type != null && !type.equalsIgnoreCase(quest.type())) continue;
            if (player != null) {
                QuestStatus actual = QuestEngine.status(player, quest);
                if (status != null ? actual != status : actual == QuestStatus.UNKNOWN) continue;
            }
            out.add(quest);
        }
        return out.toArray(new Quest[0]);
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends Quest> getReturnType() { return Quest.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        StringBuilder sb = new StringBuilder("quests");
        if (playerExpr != null) sb.append(" of ").append(playerExpr.toString(event, debug));
        if (statusExpr != null) sb.append(" with status ").append(statusExpr.toString(event, debug));
        if (categoryExpr != null) sb.append(" in category ").append(categoryExpr.toString(event, debug));
        if (typeExpr != null) sb.append(" with type ").append(typeExpr.toString(event, debug));
        return sb.toString();
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprQuestsOf.class, Quest.class)
                .addPattern("[all] quests [of %-offlineplayer%] [with status %-queststatus%] [in category %-string%] [with type %-string%]")
                .build()
        );
    }
}
