package skwhy.modules.QuestElements.expressions;

import ch.njol.skript.classes.Changer.ChangeMode;
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

@Name("[Quest] Quest NPC")
@Description("A quest NPC definition by its id (the name of its file without extension), the NPC an entity stands for, or every loaded NPC. " +
    "'quest npc of <entity>' reads the NPC listed in the NPC files for this entity. It can be set to bind any entity to a NPC for the current run " +
    "(at position 1, or at the position given with 'quest npc position of <entity>') and deleted to unbind it: useful to build NPCs from a script.")
@Examples({
    "set {_npc} to quest npc \"maire\"",
    "set {_npc} to quest npc of target entity",
    "set quest npc of {_cow} to quest npc \"maire\"",
    "loop all loaded quest npcs:",
    "\tsend \"%quest npc id of loop-value%\""
})
@Since("1.6.0")
public class ExprQuestNpc extends SimpleExpression<QuestNpc> {

    private int pattern;
    private @Nullable Expression<?> source;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        pattern = matchedPattern;
        source = pattern == 2 ? null : exprs[0];
        return true;
    }

    @Override
    protected QuestNpc @Nullable [] get(Event event) {
        if (pattern == 2) return QuestRegistry.npcs().toArray(new QuestNpc[0]);
        Object value = source.getSingle(event);
        if (value == null) return null;
        QuestNpc npc;
        if (pattern == 0) {
            NpcEntityRef ref = value instanceof Entity e ? QuestRegistry.refOf(e.getUniqueId()) : null;
            npc = ref == null ? null : QuestRegistry.npc(ref.npcId());
        } else {
            npc = QuestRegistry.npc(String.valueOf(value));
        }
        return npc == null ? null : new QuestNpc[]{ npc };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (pattern != 0) return null;
        if (mode == ChangeMode.SET) return new Class<?>[]{ QuestNpc.class };
        if (mode == ChangeMode.DELETE) return new Class<?>[0];
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (pattern != 0 || !(source.getSingle(event) instanceof Entity entity)) return;
        if (mode == ChangeMode.DELETE) {
            QuestRegistry.unbindEntity(entity.getUniqueId());
        } else if (delta != null && delta.length > 0 && delta[0] instanceof QuestNpc npc) {
            QuestRegistry.bindEntity(entity.getUniqueId(), new NpcEntityRef(npc.id(), entity.getUniqueId().toString(), 1,
                entity.getWorld().getName(), entity.getLocation().getX(), entity.getLocation().getY(), entity.getLocation().getZ()));
        }
    }

    @Override
    public boolean isSingle() { return pattern != 2; }

    @Override
    public Class<? extends QuestNpc> getReturnType() { return QuestNpc.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return switch (pattern) {
            case 0 -> "quest npc of " + source.toString(event, debug);
            case 1 -> "quest npc " + source.toString(event, debug);
            default -> "all loaded quest npcs";
        };
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprQuestNpc.class, QuestNpc.class)
                .addPattern("quest npc of %entity%")
                .addPattern("quest npc %string%")
                .addPattern("[all] loaded quest npcs")
                .priority(SyntaxInfo.PATTERN_MATCHES_EVERYTHING)
                .build()
        );
    }
}
