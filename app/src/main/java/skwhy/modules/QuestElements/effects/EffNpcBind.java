package skwhy.modules.QuestElements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.customentity.CustomEntity;
import skwhy.quest.QuestRegistry;
import skwhy.quest.model.NpcEntityRef;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.npc.CustomNpcBodies;

@Name("[Quest] Bind Entity to Quest NPC")
@Description("Makes an entity the body of a quest NPC at a position (1 by default). The link is written in the entity's NBT, so it survives restarts, chunk reloads " +
    "and copies of the entity; clicks on the entity then talk to the NPC. The place where the entity stands is recorded as that position of the NPC " +
    "(see 'quest npc location at position'), even when the entity is later unloaded. It works with any real entity, with real custom entities (a click on " +
    "their visual or on their interaction counts) and with fake custom entities (packets only: the link is kept in memory, like the fake entity itself). " +
    "A player only sees the body of the position the NPC stands at for them. 'unbind' removes the link.")
@Examples({
    "bind {_cow} to quest npc \"maire\"",
    "bind {_cow2} to quest npc \"maire\" at position 2",
    "unbind {_cow} from its quest npc"
})
@Since("1.6.1")
public class EffNpcBind extends Effect {

    private boolean bind;
    private Expression<?> target;
    private @Nullable Expression<QuestNpc> npcExpr;
    private @Nullable Expression<Number> positionExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffNpcBind.class)
                .addPattern("bind %entity/customentity% to %questnpc% [at position %-number%]")
                .addPattern("unbind %entity/customentity% from [its] quest npc")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        bind = matchedPattern == 0;
        target = exprs[0];
        if (bind) {
            npcExpr = (Expression<QuestNpc>) exprs[1];
            positionExpr = (Expression<Number>) exprs[2];
        }
        return true;
    }

    @Override
    protected void execute(Event event) {
        for (Object each : target.getArray(event)) {
            if (!bind) {
                if (each instanceof Entity entity) QuestRegistry.unbind(entity);
                else if (each instanceof CustomEntity custom) CustomNpcBodies.unbind(custom);
                continue;
            }
            QuestNpc npc = npcExpr.getSingle(event);
            if (npc == null) return;
            Number number = positionExpr == null ? null : positionExpr.getSingle(event);
            int position = number == null ? 1 : Math.max(1, number.intValue());
            NpcEntityRef ref = null;
            if (each instanceof Entity entity) ref = QuestRegistry.bind(entity, npc.id(), position);
            else if (each instanceof CustomEntity custom) ref = CustomNpcBodies.bind(custom, npc.id(), position);
            if (ref == null) {
                skwhy.SkWhy.getInstance().getLogger().warning("[Quest] bind : " + each + " n'a pas pu être lié à '" + npc.id() + "' (entité réelle absente ou détruite).");
            }
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return bind
            ? "bind " + target.toString(event, debug) + " to " + npcExpr.toString(event, debug)
                + (positionExpr == null ? "" : " at position " + positionExpr.toString(event, debug))
            : "unbind " + target.toString(event, debug) + " from its quest npc";
    }
}
