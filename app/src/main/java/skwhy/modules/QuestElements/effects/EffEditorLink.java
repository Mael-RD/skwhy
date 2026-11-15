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
import skwhy.SkWhy;
import skwhy.quest.editor.EditorService;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestNpc;

@Name("[Quest] Send Quest Editor Link")
@Description("Sends the player a link that opens the web editor on a quest or on a quest NPC (without a target, the link opens the editor on its selection menu, " +
    "which lists every quest and NPC by world with search bars). Each execution generates a new secret key, " +
    "valid for 2 hours (quest_editor.key_ttl_minutes) or until the changes are saved. In the editor the player can switch to any other quest or NPC. " +
    "When the changes are saved from the browser, the player receives a message in the chat listing what was touched (hover for the details) " +
    "and validates or cancels it, then chooses whether to renew the link (the key stays valid 5 more minutes for that). " +
    "A player who is offline at that moment is asked again when they reconnect. " +
    "The editor uses the port of the REST API (api_rest.port) but does not need the API to be started, and it has its own keys: " +
    "a request without a valid key gets no answer at all, like an API request with a wrong key. " +
    "With 'at dialogue <id>' (the id of a dialogue action in the quest or NPC script, shown in the editor) the page opens directly on the large window of that dialogue; " +
    "no link is sent if the target has no such dialogue.")
@Examples({
    "send quest editor link of quest \"chasseur\" to player",
    "send quest npc editor link of quest npc \"frieren\" to player",
    "send quest editor link to player",
    "send quest npc editor link of quest npc \"frieren\" at dialogue \"3f2a9c1e-0000-4000-8000-000000000001\" to player"
})
@Since("1.6.0")
public class EffEditorLink extends Effect {

    private boolean npc;
    private boolean menu;
    private Expression<String> dialogueExpr;
    private Expression<?> targetExpr;
    private Expression<Player> playersExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffEditorLink.class)
                .addPattern("send [a] [new] quest editor link of %quest% to %players%")
                .addPattern("send [a] [new] quest npc editor link of %questnpc% to %players%")
                .addPattern("send [a] [new] quest editor link to %players%")
                .addPattern("send [a] [new] quest editor link of %quest% (at|on) dialogue %string% to %players%")
                .addPattern("send [a] [new] quest npc editor link of %questnpc% (at|on) dialogue %string% to %players%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        npc = matchedPattern == 1 || matchedPattern == 4;
        menu = matchedPattern == 2;
        targetExpr = menu ? null : exprs[0];
        dialogueExpr = matchedPattern >= 3 ? (Expression<String>) exprs[1] : null;
        playersExpr = (Expression<Player>) exprs[menu ? 0 : dialogueExpr == null ? 1 : 2];
        return true;
    }

    @Override
    protected void execute(Event event) {
        Object target = menu ? null : targetExpr.getSingle(event);
        String id = menu ? "" : target instanceof Quest q ? q.id() : target instanceof QuestNpc n ? n.id() : null;
        if (id == null) return;
        String dialogue = dialogueExpr == null ? null : dialogueExpr.getSingle(event);
        if (dialogueExpr != null && dialogue == null) return;
        for (Player player : playersExpr.getArray(event)) {
            if (!EditorService.sendLink(player, menu ? "" : npc ? "npc" : "quest", id, dialogue)) {
                SkWhy.getInstance().getLogger().warning("[QuestEditor] Lien non envoyé à " + player.getName()
                    + " : éditeur indisponible (quest_editor.enabled, serveur HTTP), " + id + " introuvable"
                    + (dialogue == null ? "." : " ou sans dialogue \"" + dialogue + "\"."));
            }
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        if (menu) return "send quest editor link to " + playersExpr.toString(event, debug);
        return "send quest " + (npc ? "npc " : "") + "editor link of " + targetExpr.toString(event, debug)
            + (dialogueExpr == null ? "" : " at dialogue " + dialogueExpr.toString(event, debug)) + " to " + playersExpr.toString(event, debug);
    }
}
