package skwhy.modules.RandomStuff.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.doc.RequiredPlugins;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Name("Secretly Hide Entity")
@Description("Hides one or more entities for one or more players using Bukkit's native visibility system " +
        "(Player#hideEntity), but without sending the entity destroy packet. The entity remains visible " +
        "client-side in its last known state until the player reconnects, receiving no further updates " +
        "(position, metadata, etc). Since this relies on Bukkit's native system, it stays compatible with " +
        "other effects that hide or reveal entities. Use Skript's built-in 'reveal %entity% for %player%' " +
        "effect to undo this. As the entity is already hidden, you have to reveal and re-hide it to make is " +
        "invisible for real. It will also became totally invisible if the player unload it.")
@Examples({
    "secretly hide target entity for player",
    "secretly hide all zombies within 10 blocks of player for all players"
})
@Since("1.3.1")
@RequiredPlugins("PacketEvents")
public class SecretHide extends Effect {

    // (uuid joueur + entityId) en attente : le prochain packet de
    // suppression pour cette paire doit être filtré, une seule fois
    private static final Set<String> pendingSuppression = ConcurrentHashMap.newKeySet();
    private static boolean listenerRegistered = false;

    private static synchronized void ensureListenerRegistered() {
        if (listenerRegistered) return;
        listenerRegistered = true;

        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract() {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                if (event.getPacketType() != PacketType.Play.Server.DESTROY_ENTITIES) return;
                if (!(event.getPlayer() instanceof Player player)) return;

                WrapperPlayServerDestroyEntities wrapper = new WrapperPlayServerDestroyEntities(event);
                int[] ids = wrapper.getEntityIds();

                String uuid = player.getUniqueId().toString();
                int[] filtered = Arrays.stream(ids)
                        .filter(id -> !pendingSuppression.remove(uuid + ":" + id))
                        .toArray();

                if (filtered.length == ids.length) {
                    return; // rien à filtrer pour ce joueur
                }

                if (filtered.length == 0) {
                    event.setCancelled(true); // le packet ne concernait que des entités à masquer
                } else {
                    wrapper.setEntityIds(filtered); // on retire juste nos IDs, le reste part normalement
                }
            }
        });
    }

    private Expression<Entity> entities;
    private Expression<Player> players;
    private Plugin owningPlugin;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, ParseResult pr) {

        //noinspection unchecked
        this.entities = (Expression<Entity>) exprs[0];
        //noinspection unchecked
        this.players = (Expression<Player>) exprs[1];

        // Instance de ton JavaPlugin : sert de "clé" au tracking natif de
        // Bukkit, à garder identique à celle utilisée par tes autres
        // effets hide/reveal pour rester compatible entre eux.
        this.owningPlugin = ch.njol.skript.Skript.getInstance();;

        return true;
    }

    @Override
    protected void execute(Event event) {
        Entity[] targetEntities = entities.getAll(event);
        Player[] targetPlayers = players.getAll(event);

        if (targetEntities == null || targetPlayers == null) return;

        ensureListenerRegistered();

        for (Player player : targetPlayers) {
            for (Entity entity : targetEntities) {
                pendingSuppression.add(player.getUniqueId() + ":" + entity.getEntityId());
                player.hideEntity(owningPlugin, entity);
            }
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "secretly hide " + entities.toString(event, debug) + " for " + players.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(SecretHide.class)
                .addPattern("secretly hide %entities% for %players%")
                .build()
        );
    }
}