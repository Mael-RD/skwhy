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
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.modules.QuestElements.events.QuestNpcNameLearnEvent;
import skwhy.quest.data.PlayerQuestData;
import skwhy.quest.data.QuestStore;
import skwhy.quest.model.QuestNpc;

@Name("[Quest] Known Name")
@Description("Whether a player knows the name of a quest NPC. Until then the NPC is called ' ??? ' in its messages. " +
    "A player learns it when the NPC says its own name, or when it is set to true. Setting it to false makes the NPC a stranger again. " +
    "NPCs whose name is always visible are known by everybody.")
@Examples({
    "set known name of quest npc \"maire\" for player to true",
    "if known name of quest npc \"maire\" for player is false:",
    "\tsend \"Tu ne connais pas encore son nom.\""
})
@Since("1.6.0")
public class ExprKnownName extends SimpleExpression<Boolean> {

    private Expression<QuestNpc> npcExpr;
    private Expression<OfflinePlayer> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        npcExpr = (Expression<QuestNpc>) exprs[0];
        playerExpr = (Expression<OfflinePlayer>) exprs[1];
        return true;
    }

    @Override
    protected Boolean @Nullable [] get(Event event) {
        QuestNpc npc = npcExpr.getSingle(event);
        OfflinePlayer player = playerExpr.getSingle(event);
        if (npc == null || player == null) return null;
        return new Boolean[]{ npc.nameVisible() || QuestStore.get(player.getUniqueId()).knownNames.contains(npc.id()) };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ Boolean.class };
        if (mode == ChangeMode.RESET) return new Class<?>[0];
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        QuestNpc npc = npcExpr.getSingle(event);
        OfflinePlayer player = playerExpr.getSingle(event);
        if (npc == null || player == null) return;
        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        boolean known = mode == ChangeMode.SET && delta != null && delta.length > 0 && delta[0] instanceof Boolean b && b;
        if (known) {
            if (data.knownNames.add(npc.id())) {
                data.markDirty();
                Bukkit.getPluginManager().callEvent(new QuestNpcNameLearnEvent(player, npc));
            }
        } else if (data.knownNames.remove(npc.id())) {
            data.markDirty();
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Boolean> getReturnType() { return Boolean.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "known name of " + npcExpr.toString(event, debug) + " for " + playerExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprKnownName.class, Boolean.class)
                .addPattern("known name of %questnpc% for %offlineplayer%")
                .build()
        );
    }
}
