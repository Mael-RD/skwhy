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
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.QuestRegistry;
import skwhy.quest.model.NpcEntityRef;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.npc.NpcPlaces;
import skwhy.quest.npc.NpcService;

@Name("[Quest] Quest NPC Places")
@Description("The recorded places of a quest NPC. 'quest npc location at position <n> of <npc>' is where the NPC stands at that position: recorded by 'bind' " +
    "(file plugins/SkWhy/quest-npc-positions.yml) or read from the NPC file; it does not depend on any entity being loaded. It can be set to a location " +
    "(the compass target of the NPC, and where 'move quest npc' walks it to) and deleted to forget a position. " +
    "'quest npc positions of <npc>' is the list of the position numbers the NPC has.")
@Examples({
    "set {_where} to quest npc location at position 2 of quest npc \"maire\"",
    "set quest npc location at position 3 of quest npc \"maire\" to location(10, 70, 10)",
    "loop quest npc positions of quest npc \"maire\":",
    "\tsend \"%loop-value%\""
})
@Since("1.6.1")
public class ExprNpcPlace extends SimpleExpression<Object> {

    private int pattern;
    private @Nullable Expression<Number> positionExpr;
    private Expression<QuestNpc> npcExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        pattern = matchedPattern;
        if (pattern == 0) {
            positionExpr = (Expression<Number>) exprs[0];
            npcExpr = (Expression<QuestNpc>) exprs[1];
        } else {
            npcExpr = (Expression<QuestNpc>) exprs[0];
        }
        return true;
    }

    @Override
    protected Object @Nullable [] get(Event event) {
        QuestNpc npc = npcExpr.getSingle(event);
        if (npc == null) return null;
        if (pattern == 1) {
            return QuestRegistry.places(npc).stream().map(NpcEntityRef::position).toArray(Integer[]::new);
        }
        Number number = positionExpr.getSingle(event);
        if (number == null) return null;
        NpcEntityRef ref = QuestRegistry.entityAt(npc, number.intValue());
        if (ref == null) return null;
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(ref.world());
        NpcPlaces.Place place = NpcPlaces.get(npc.id(), ref.position());
        float yaw = place == null ? 0f : place.yaw();
        float pitch = place == null ? 0f : place.pitch();
        return world == null ? null : new Location[]{ new Location(world, ref.x(), ref.y(), ref.z(), yaw, pitch) };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (pattern != 0) return null;
        return switch (mode) {
            case SET -> new Class<?>[]{ Location.class };
            case DELETE -> new Class<?>[0];
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        QuestNpc npc = npcExpr.getSingle(event);
        Number number = positionExpr.getSingle(event);
        if (npc == null || number == null) return;
        int position = Math.max(1, number.intValue());
        if (mode == ChangeMode.DELETE) {
            NpcPlaces.remove(npc.id(), position);
        } else if (delta != null && delta.length > 0 && delta[0] instanceof Location location && location.getWorld() != null) {
            NpcPlaces.set(npc.id(), position, NpcPlaces.Place.of(location));
        }
        NpcService.refreshAll();
    }

    @Override
    public boolean isSingle() { return pattern == 0; }

    @Override
    public Class<?> getReturnType() { return pattern == 0 ? Location.class : Integer.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return pattern == 0
            ? "quest npc location at position " + positionExpr.toString(event, debug) + " of " + npcExpr.toString(event, debug)
            : "quest npc positions of " + npcExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprNpcPlace.class, Object.class)
                .addPattern("quest npc location at position %number% of %questnpc%")
                .addPattern("quest npc positions of %questnpc%")
                .build()
        );
    }
}
