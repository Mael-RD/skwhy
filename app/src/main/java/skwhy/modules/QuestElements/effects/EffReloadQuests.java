package skwhy.modules.QuestElements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.QuestRegistry;
import skwhy.quest.data.QuestStore;
import skwhy.quest.npc.IndicatorService;

@Name("[Quest] Reload Quests / Save Quest Data")
@Description("Reloads the quest files (all of them, one quest by its id, all the NPCs or one NPC by its id) without touching the progress of the players, " +
    "or writes the progress of all players to disk at once (it is also written regularly and when a player leaves). " +
    "Quests created by 'create quest' in a script have no file: reloading one of them by its id, or reloading all quests, forgets it " +
    "(create it again, for example when your scripts load).")
@Examples({
    "reload quests",
    "reload quest \"chasseur\"",
    "reload quest npcs",
    "reload quest npc \"maire\"",
    "save quest data"
})
@Since("1.6.0")
public class EffReloadQuests extends Effect {

    private int mode;
    private @Nullable Expression<String> idExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffReloadQuests.class)
                .addPattern("reload [all] quests")
                .addPattern("reload quest %string%")
                .addPattern("reload [all] quest npcs")
                .addPattern("reload quest npc %string%")
                .addPattern("save quest data")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        mode = matchedPattern;
        idExpr = mode == 1 || mode == 3 ? (Expression<String>) exprs[0] : null;
        return true;
    }

    @Override
    protected void execute(Event event) {
        String id = idExpr == null ? null : idExpr.getSingle(event);
        switch (mode) {
            case 0, 2 -> QuestRegistry.reloadAll();
            case 1 -> { if (id != null) QuestRegistry.reloadQuest(id); }
            case 3 -> { if (id != null) QuestRegistry.reloadNpc(id); }
            default -> QuestStore.saveDirty();
        }
        if (mode != 4) {
            for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                skwhy.quest.data.PlayerQuestData data = QuestStore.getIfLoaded(player.getUniqueId());
                if (data != null) skwhy.quest.QuestEngine.reindex(data);
                IndicatorService.invalidate(player.getUniqueId());
            }
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return switch (mode) {
            case 0 -> "reload quests";
            case 1 -> "reload quest " + idExpr.toString(event, debug);
            case 2 -> "reload quest npcs";
            case 3 -> "reload quest npc " + idExpr.toString(event, debug);
            default -> "save quest data";
        };
    }
}
