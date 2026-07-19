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
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Name("Secretly Reveal Entity")
@Description("Re-adds one or more entities to the list of entities visible/tracked for one or more players " +
        "(via Bukkit's native Player#showEntity), removing them from the secretly-hidden state, but without " +
        "informing the client in any way: the spawn packet Bukkit would normally send as a side effect of " +
        "showEntity is silently swallowed. Meant to be used on entities that were previously hidden with " +
        "'secretly hide %entities% for %players%': since the client never received a destroy packet, it " +
        "still displays the entity in its last known (frozen) state, so a spawn packet would be redundant " +
        "or would visually reset it. After this effect, the player resumes receiving normal updates " +
        "(position, metadata, etc) for the entity, seamlessly continuing from where the client left off.")
@Examples({
    "secretly hide target entity for player",
    "wait 5 seconds",
    "secretly reveal target entity for player"
})
@Since("1.3.1")
@RequiredPlugins("PacketEvents")
public class SecretReveal extends Effect {

    // (uuid joueur + entityId) en attente : le prochain packet de
    // spawn pour cette paire doit être filtré, une seule fois
    private static final Set<String> pendingSuppression = ConcurrentHashMap.newKeySet();
    private static boolean listenerRegistered = false;

    private static synchronized void ensureListenerRegistered() {
        if (listenerRegistered) return;
        listenerRegistered = true;

        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract() {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                if (!(event.getPlayer() instanceof Player player)) return;

                String uuid = player.getUniqueId().toString();

                if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
                    WrapperPlayServerSpawnEntity wrapper = new WrapperPlayServerSpawnEntity(event);
                    int id = wrapper.getEntityId();

                    if (pendingSuppression.remove(uuid + ":" + id)) {
                        event.setCancelled(true);
                    }
                    return;
                }

                // Sur les versions où le joueur a son propre packet de spawn
                if (event.getPacketType() == PacketType.Play.Server.SPAWN_PLAYER) {
                    WrapperPlayServerSpawnPlayer wrapper = new WrapperPlayServerSpawnPlayer(event);
                    int id = wrapper.getEntityId();

                    if (pendingSuppression.remove(uuid + ":" + id)) {
                        event.setCancelled(true);
                    }
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

        this.owningPlugin = ch.njol.skript.Skript.getInstance();

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
                player.showEntity(owningPlugin, entity);
            }
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "secretly reveal " + entities.toString(event, debug) + " for " + players.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(SecretReveal.class)
                .addPattern("secretly reveal %entities% for %players%")
                .build()
        );
    }
}