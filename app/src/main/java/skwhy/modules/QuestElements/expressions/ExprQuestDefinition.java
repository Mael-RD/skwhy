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
import skwhy.quest.QuestDefinitions;
import skwhy.quest.model.Quest;
import skwhy.quest.model.QuestNpc;

@Name("[Quest] Quest Definition / Quest NPC Definition")
@Description("The whole definition of a quest or of a quest NPC (steps, objectives, scripts, dialogues...) as NBT text (SNBT). " +
    "Scripts that edit definitions (an in-game editor) turn it into an NBT compound with 'nbt compound from' " +
    "and write it back with 'save quest definition'.")
@Examples({
    "set {_nbt} to nbt compound from quest definition of quest \"chasseur\"",
    "send string tag \"name\" of {_nbt}"
})
@Since("1.6.0")
public class ExprQuestDefinition extends SimpleExpression<String> {

    private boolean npc;
    private Expression<?> source;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        npc = matchedPattern == 1;
        source = exprs[0];
        return true;
    }

    @Override
    protected String @Nullable [] get(Event event) {
        Object value = source.getSingle(event);
        if (value instanceof Quest quest && !npc) return new String[]{ QuestDefinitions.snbt(quest) };
        if (value instanceof QuestNpc questNpc && npc) return new String[]{ QuestDefinitions.snbt(questNpc) };
        return new String[0];
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return (npc ? "quest npc" : "quest") + " definition of " + source.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprQuestDefinition.class, String.class)
                .addPattern("quest definition of %quest%")
                .addPattern("quest npc definition of %questnpc%")
                .build()
        );
    }
}
