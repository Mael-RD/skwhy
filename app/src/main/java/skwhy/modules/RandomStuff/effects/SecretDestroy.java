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
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.List;

@Name("Secretly Destroy Entity")
@Description("Sends a fake entity destroy packet to one or more players for one or more entities (or raw " +
        "entity IDs), without touching Bukkit's native visibility system and without informing the server " +
        "in any way. Intended to be used either on entities that were previously hidden with 'secretly hide' " +
        "(to make them disappear from the client after their state has already been frozen), or on entity " +
        "IDs that do not correspond to any real entity (fake/spoofed IDs), since sending a destroy packet for " +
        "a non-existent entity has no effect on the server and is a safe way to clean up client-side ghosts. " +
        "Using this on a real, currently tracked and visible entity isn't a good idea.")
@Examples({
    "secretly destroy target entity for player",
    "secretly destroy all zombies within 10 blocks of player for all players",
    "secretly destroy 145 for player # raw entity id"
})
@Since("1.3.1")
@RequiredPlugins("PacketEvents")
public class SecretDestroy extends Effect {

    private Expression<?> targets; // Entity ou Number (entity id brut)
    private Expression<Player> players;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, ParseResult pr) {

        this.targets = exprs[0];
        //noinspection unchecked
        this.players = (Expression<Player>) exprs[1];

        return true;
    }

    @Override
    protected void execute(Event event) {
        Object[] rawTargets = targets.getArray(event);
        Player[] targetPlayers = players.getAll(event);

        if (rawTargets == null || targetPlayers == null || rawTargets.length == 0) return;

        // Résolution des IDs une seule fois, peu importe le type d'entrée
        List<Integer> ids = new ArrayList<>(rawTargets.length);
        for (Object o : rawTargets) {
            if (o instanceof Entity entity) {
                ids.add(entity.getEntityId());
            } else if (o instanceof Number number) {
                ids.add(number.intValue());
            }
        }

        if (ids.isEmpty()) return;

        int[] idArray = ids.stream().mapToInt(i -> i).toArray();

        for (Player player : targetPlayers) {
            User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user == null) continue;

            // On construit et on envoie directement le packet, sans passer
            // par event.setCancelled ni par aucune API Bukkit : le serveur
            // ignore totalement que ce packet a été envoyé.
            WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(idArray);
            user.sendPacket(packet);
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "secretly destroy " + targets.toString(event, debug) + " for " + players.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(SecretDestroy.class)
                .addPattern("secretly destroy %objects% for %players%")
                .build()
        );
    }
}