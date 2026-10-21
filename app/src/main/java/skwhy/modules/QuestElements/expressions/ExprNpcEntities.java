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
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.QuestRegistry;
import skwhy.quest.model.NpcEntityRef;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.npc.NpcService;

@Name("[Quest] Quest NPC Entities")
@Description("The entities of the world that stand for a quest NPC, at all its positions (including entities bound with 'set quest npc of ...'). " +
    "Entities that are not loaded are skipped.")
@Examples({
    "loop quest entities of quest npc \"maire\":",
    "\tteleport loop-value to {_spawn}"
})
@Since("1.6.0")
public class ExprNpcEntities extends SimpleExpression<Entity> {

    private Expression<QuestNpc> npcExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        npcExpr = (Expression<QuestNpc>) exprs[0];
        return true;
    }

    @Override
    protected Entity @Nullable [] get(Event event) {
        QuestNpc npc = npcExpr.getSingle(event);
        if (npc == null) return null;
        List<Entity> out = new ArrayList<>();
        for (NpcEntityRef ref : QuestRegistry.entityRefs()) {
            if (!ref.npcId().equals(npc.id())) continue;
            Entity entity = NpcService.entity(ref);
            if (entity != null) out.add(entity);
        }
        return out.toArray(new Entity[0]);
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends Entity> getReturnType() { return Entity.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "quest entities of " + npcExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprNpcEntities.class, Entity.class)
                .addPattern("quest entities of %questnpc%")
                .build()
        );
    }
}
