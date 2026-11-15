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
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.customentity.CustomEntity;
import skwhy.quest.QuestRegistry;
import skwhy.quest.npc.CustomNpcBodies;
import skwhy.quest.model.NpcEntityRef;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.npc.NpcService;

@Name("[Quest] Quest NPC Position")
@Description("A quest NPC can stand at several places (positions 1, 2, 3...) and a player sees it at one of them. " +
    "'quest npc position of <npc> for <player>' is the position that player sees: setting it moves the NPC for this player at once " +
    "(use 'move quest npc' for a walk). 'quest npc position of <entity>' is the position an entity stands for (1 by default); " +
    "setting it re-binds the entity to the same NPC at another position (and records where it stands as that position). It works with custom entities, real or fake. " +
    "Positions start at 1.")
@Examples({
    "set quest npc position of quest npc \"maire\" for player to 2",
    "send \"%quest npc position of quest npc \"maire\" for player%\"",
    "set quest npc position of {_cow2} to 2"
})
@Since("1.6.0")
public class ExprNpcPosition extends SimpleExpression<Integer> {

    private int pattern;
    private Expression<?> source;
    private @Nullable Expression<OfflinePlayer> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        pattern = matchedPattern;
        source = exprs[0];
        playerExpr = pattern == 0 ? (Expression<OfflinePlayer>) exprs[1] : null;
        return true;
    }

    @Override
    protected Integer @Nullable [] get(Event event) {
        if (pattern == 0) {
            QuestNpc npc = (QuestNpc) source.getSingle(event);
            OfflinePlayer player = playerExpr.getSingle(event);
            if (npc == null || player == null) return null;
            return new Integer[]{ NpcService.position(player, npc) };
        }
        NpcEntityRef ref = ExprQuestNpc.refOf(source.getSingle(event));
        return ref == null ? null : new Integer[]{ ref.position() };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        return mode == ChangeMode.SET ? new Class<?>[]{ Number.class } : null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (delta == null || delta.length == 0 || !(delta[0] instanceof Number number)) return;
        int position = Math.max(1, number.intValue());
        if (pattern == 0) {
            QuestNpc npc = (QuestNpc) source.getSingle(event);
            OfflinePlayer player = playerExpr.getSingle(event);
            if (npc != null && player != null) NpcService.setPosition(player, npc, position);
            return;
        }
        Object target = source.getSingle(event);
        NpcEntityRef ref = ExprQuestNpc.refOf(target);
        if (ref == null) return;
        if (target instanceof Entity entity) QuestRegistry.bind(entity, ref.npcId(), position);
        else if (target instanceof CustomEntity custom) CustomNpcBodies.bind(custom, ref.npcId(), position);
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Integer> getReturnType() { return Integer.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return pattern == 0
            ? "quest npc position of " + source.toString(event, debug) + " for " + playerExpr.toString(event, debug)
            : "quest npc position of " + source.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprNpcPosition.class, Integer.class)
                .addPattern("quest npc position of %questnpc% for %offlineplayer%")
                .addPattern("quest npc position of %entity/customentity%")
                .build()
        );
    }
}
