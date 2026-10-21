package skwhy.modules.QuestElements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.RequiredPlugins;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.quest.QuestRegistry;
import skwhy.quest.model.NpcEntityRef;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.npc.NpcService;

@Name("[Quest] Make Quest NPC Look At")
@Description("Turns the head and body of a quest NPC towards an entity or a location, for the given players only (the real entity does not move). " +
    "Every entity of the NPC that is loaded turns, at all its positions.")
@Examples({
    "make quest npc \"maire\" look at player for player",
    "make quest npc \"maire\" face location(0, 70, 0) for all players"
})
@Since("1.6.0")
@RequiredPlugins("PacketEvents")
public class EffNpcLook extends Effect {

    private Expression<QuestNpc> npcExpr;
    private Expression<?> targetExpr;
    private Expression<Player> playersExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffNpcLook.class)
                .addPattern("make %questnpc% (look at|face) %entity/location% for %players%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        npcExpr = (Expression<QuestNpc>) exprs[0];
        targetExpr = exprs[1];
        playersExpr = (Expression<Player>) exprs[2];
        return true;
    }

    @Override
    protected void execute(Event event) {
        QuestNpc npc = npcExpr.getSingle(event);
        Object target = targetExpr.getSingle(event);
        if (npc == null || target == null || PacketEvents.getAPI() == null) return;
        Location look = target instanceof org.bukkit.entity.LivingEntity living ? living.getEyeLocation()
            : target instanceof Entity e ? e.getLocation() : target instanceof Location l ? l : null;
        if (look == null) return;
        for (NpcEntityRef ref : QuestRegistry.entityRefs()) {
            if (!ref.npcId().equals(npc.id())) continue;
            Entity entity = NpcService.entity(ref);
            if (entity == null) continue;
            Location from = entity.getLocation();
            double dx = look.getX() - from.getX();
            double dz = look.getZ() - from.getZ();
            double dy = look.getY() - (from.getY() + entity.getHeight() * 0.9);
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            for (Player player : playersExpr.getArray(event)) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerEntityRotation(entity.getEntityId(), yaw, pitch, entity.isOnGround()));
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerEntityHeadLook(entity.getEntityId(), yaw));
            }
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "make " + npcExpr.toString(event, debug) + " look at " + targetExpr.toString(event, debug) + " for " + playersExpr.toString(event, debug);
    }
}
