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
import skwhy.quest.QuestDefinitions;
import skwhy.quest.data.QuestStore;
import skwhy.quest.npc.IndicatorService;

@Name("[Quest] Save Quest Definition / Save Quest NPC Definition")
@Description("Writes the definition of a quest (or of a quest NPC) given as NBT text (SNBT, for example the text of an NBT compound) " +
    "to its YAML file in SkWhy's quest (or quest-npcs) folder, then reloads it. The YAML file takes precedence over an old .nbt file of the same id. " +
    "Progress of the players is untouched.")
@Examples({
    "set {_nbt} to nbt compound from quest definition of quest \"chasseur\"",
    "set string tag \"name\" of {_nbt} to \"<gold>Chasseur\"",
    "save quest definition \"chasseur\" from \"%{_nbt}%\""
})
@Since("1.6.0")
public class EffSaveDefinition extends Effect {

    private boolean npc;
    private Expression<String> idExpr;
    private Expression<String> textExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffSaveDefinition.class)
                .addPattern("save quest definition %string% from %string%")
                .addPattern("save quest npc definition %string% from %string%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        npc = matchedPattern == 1;
        idExpr = (Expression<String>) exprs[0];
        textExpr = (Expression<String>) exprs[1];
        return true;
    }

    @Override
    protected void execute(Event event) {
        String id = idExpr.getSingle(event);
        String text = textExpr.getSingle(event);
        if (id == null || text == null || id.isBlank()) return;
        if (!(npc ? QuestDefinitions.saveNpc(id, text) : QuestDefinitions.saveQuest(id, text))) return;
        for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            skwhy.quest.data.PlayerQuestData data = QuestStore.getIfLoaded(player.getUniqueId());
            if (data != null) skwhy.quest.QuestEngine.reindex(data);
            IndicatorService.invalidate(player.getUniqueId());
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "save quest " + (npc ? "npc " : "") + "definition " + idExpr.toString(event, debug) + " from " + textExpr.toString(event, debug);
    }
}
