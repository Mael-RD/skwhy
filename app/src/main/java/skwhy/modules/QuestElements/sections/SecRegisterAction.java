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

@Name("[Quest] Register Quest Action")
@Description("Creates a new type of action for the quest and NPC scripts: once registered, a script node with 'action: <name>' runs this section. " +
    "Run it when your scripts load ('on load'). Inside, 'event-player' (or the local variable {_player}) is the player, {_npc} the quest NPC and " +
    "{_entity} its entity when there is one, and {_args::*} holds the other keys of the script node (for example the node " +
    "'action: give_ring, color: gold' gives {_args::color} = \"gold\"). The next action of the script runs right after the section started: " +
    "a 'wait' inside the section does not delay it. Registering a name again replaces the previous section.")
@Examples({
    "on load:",
    "\tregister quest action \"give_ring\":",
    "\t\tgive a gold ingot named \"Bague\" to {_player}",
    "\t\tsend \"Voici une bague %{_args::color}%\" to {_player}"
})
@Since("1.6.0")
public class SecRegisterAction extends Section {

    private Expression<String> nameExpr;
    private Trigger trigger;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr,
                        @Nullable SectionNode sectionNode, List<TriggerItem> triggerItems) {
        if (sectionNode == null) {
            Skript.error("'register quest action' needs a section (add ':' at the end of the line).");
            return false;
        }
        nameExpr = (Expression<String>) exprs[0];
        trigger = loadCode(sectionNode, "quest action", QuestScriptEvent.class);
        return true;
    }

    @Override
    protected @Nullable TriggerItem walk(Event event) {
        String name = nameExpr.getSingle(event);
        if (name != null && !name.isBlank()) {
            Trigger body = trigger;
            CustomHandlers.registerAction(name, (ctx, node, done) -> {
                Map<String, Object> arguments = new LinkedHashMap<>(node.data());
                arguments.remove("action");
                QuestScriptEvent scriptEvent = new QuestScriptEvent(ctx.player(), ctx.npc(), ctx.entity(), arguments);
                SectionLocals.set(scriptEvent);
                try {
                    body.execute(scriptEvent);
                } finally {
                    done.run();
                }
            });
        }
        return walk(event, false);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "register quest action " + nameExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.SECTION,
            SyntaxInfo.builder(SecRegisterAction.class)
                .addPattern("register quest action %string%")
                .build()
        );
    }
}
