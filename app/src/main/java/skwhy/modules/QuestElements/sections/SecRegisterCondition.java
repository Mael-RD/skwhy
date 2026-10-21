package skwhy.modules.QuestElements.sections;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ch.njol.skript.Skript;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Section;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.modules.QuestElements.events.QuestScriptEvent;
import skwhy.quest.script.CustomHandlers;

@Name("[Quest] Register Quest Condition")
@Description("Creates a new type of condition for the quest and NPC scripts: once registered, a script node with 'condition: <name>' " +
    "(or an action whose 'condition' is this name) runs this section to decide. The condition is true, unless the section cancels the event. " +
    "Run it when your scripts load ('on load'). Inside, 'event-player' (or the local variable {_player}) is the player, {_npc} the quest NPC and " +
    "{_entity} its entity when there is one, and {_args::*} holds the other keys of the script node. Registering a name again replaces the previous section. " +
    "The section must be fast and must not wait.")
@Examples({
    "on load:",
    "\tregister quest condition \"owns_ring\":",
    "\t\tif {_player} doesn't have a gold ingot named \"Bague\":",
    "\t\t\tcancel event"
})
@Since("1.6.0")
public class SecRegisterCondition extends Section {

    private Expression<String> nameExpr;
    private Trigger trigger;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr,
                        @Nullable SectionNode sectionNode, List<TriggerItem> triggerItems) {
        if (sectionNode == null) {
            Skript.error("'register quest condition' needs a section (add ':' at the end of the line).");
            return false;
        }
        nameExpr = (Expression<String>) exprs[0];
        trigger = loadCode(sectionNode, "quest condition", QuestScriptEvent.class);
        return true;
    }

    @Override
    protected @Nullable TriggerItem walk(Event event) {
        String name = nameExpr.getSingle(event);
        if (name != null && !name.isBlank()) {
            Trigger body = trigger;
            CustomHandlers.registerCondition(name, (ctx, node) -> {
                Map<String, Object> arguments = new LinkedHashMap<>(node.data());
                arguments.remove("condition");
                QuestScriptEvent scriptEvent = new QuestScriptEvent(ctx.player(), ctx.npc(), ctx.entity(), arguments);
                SectionLocals.set(scriptEvent);
                body.execute(scriptEvent);
                return !scriptEvent.isCancelled();
            });
        }
        return walk(event, false);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "register quest condition " + nameExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.SECTION,
            SyntaxInfo.builder(SecRegisterCondition.class)
                .addPattern("register quest condition %string%")
                .build()
        );
    }
}
