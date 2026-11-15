package skwhy.chunkpath.debug;

import java.util.List;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Console log of the entity packets sent by the (long) navigations ({@code /cpdebug set packets on}). */
public final class PacketLog {
    private PacketLog() {}

    public static void move(String source, int entityId, double dx, double dy, double dz, Location server, List<Player> viewers) {
        if (!ChunkPathTuning.packets) return;
        log(source, "RELATIVE_MOVE id=" + entityId + String.format(" delta=(%.4f, %.4f, %.4f)", dx, dy, dz), server, viewers);
    }

    public static void teleport(String source, int entityId, Location sent, Location server, List<Player> viewers) {
        if (!ChunkPathTuning.packets) return;
        log(source, "TELEPORT id=" + entityId + String.format(" to=(%.3f, %.3f, %.3f)", sent.getX(), sent.getY(), sent.getZ()), server, viewers);
    }

    private static void log(String source, String what, Location server, List<Player> viewers) {
        StringBuilder sb = new StringBuilder("[Packets/").append(source).append("] ").append(what);
        if (server != null) sb.append(String.format(" server=(%.3f, %.3f, %.3f)", server.getX(), server.getY(), server.getZ()));
        for (Player p : viewers) {
            Location l = p.getLocation();
            sb.append(" | ").append(p.getName());
            if (server != null && l.getWorld() == server.getWorld()) sb.append(String.format(" dist=%.1f", l.distance(server)));
            else sb.append(" other-world");
        }
        if (viewers.isEmpty()) sb.append(" | NO VIEWERS");
        skwhy.SkWhy.getInstance().getLogger().log(Level.INFO, sb.toString());
    }
}
