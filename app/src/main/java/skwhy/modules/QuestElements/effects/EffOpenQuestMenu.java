package skwhy.modules.QuestElements.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.QuestSettings;
import skwhy.quest.script.SkriptFunctions;

@Name("[Quest] Open Quest Menu")
@Description("Opens the quest menu of your server to players, optionally for another player (to look at his quests). " +
    "SkWhy has no menu of its own: the menu is a Skript function, named by the config key 'quests.menu_function' " +
    "(default 'quest_répertoire_open'), that is called with (player, none, other player). " +
    "A warning is printed when the function does not exist.")
@Examples({
    "open quest menu for player",
    "open quest menu for player of {_target}"
})
@Since("1.6.0")
public class EffOpenQuestMenu extends Effect {

    private Expression<Player> playersExpr;
    private @Nullable Expression<OfflinePlayer> targetExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffOpenQuestMenu.class)
                .addPattern("open quest menu for %players% [of %-offlineplayer%]")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        playersExpr = (Expression<Player>) exprs[0];
        targetExpr = (Expression<OfflinePlayer>) exprs[1];
        return true;
    }

    @Override
    protected void execute(Event event) {
        String function = QuestSettings.menuFunction();
        if (!SkriptFunctions.exists(function)) {
            Skript.warning("The quest menu function '" + function + "' does not exist (config key quests.menu_function).");
            return;
        }
        OfflinePlayer target = targetExpr == null ? null : targetExpr.getSingle(event);
        for (Player player : playersExpr.getArray(event)) SkriptFunctions.call(function, player, null, target);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "open quest menu for " + playersExpr.toString(event, debug) + (targetExpr == null ? "" : " of " + targetExpr.toString(event, debug));
    }
}
