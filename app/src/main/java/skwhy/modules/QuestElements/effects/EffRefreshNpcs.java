package skwhy.modules.QuestElements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.npc.NpcService;

@Name("[Quest] Refresh Quest NPCs")
@Description("Recomputes which positions of the quest NPCs these players see. Needed after giving or removing the metadata " +
    "'quest_see_all_npcs' (staff members with the quest staff permission see every position of every NPC while they have it).")
@Examples({
    "set metadata \"quest_see_all_npcs\" of player to true",
    "refresh quest npcs for player"
})
@Since("1.6.0")
public class EffRefreshNpcs extends Effect {

    private Expression<Player> playersExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffRefreshNpcs.class)
                .addPattern("refresh quest npcs for %players%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        playersExpr = (Expression<Player>) exprs[0];
        return true;
    }

    @Override
    protected void execute(Event event) {
        for (Player player : playersExpr.getArray(event)) NpcService.refreshVisibility(player);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "refresh quest npcs for " + playersExpr.toString(event, debug);
    }
}
