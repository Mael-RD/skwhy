package skwhy.modules.RandomStuff.events;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange.EncodedBlock;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.List;

/**
 * Fired when a block update packet (single or multi block change) is sent to a player.
 * Cancelling the event removes that block from the packet.
 * The event is asynchronous when the packet is sent outside the main thread.
 */
public class BlockChangePacket extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location location;
    private final BlockData blockData;
    private boolean cancelled;

    public BlockChangePacket(Player player, Location location, BlockData blockData) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.location = location;
        this.blockData = blockData;
    }

    public Player getPlayer()        { return player; }
    public Location getLocation()    { return location.clone(); }
    public BlockData getBlockData()  { return blockData.clone(); }
    /** The real block at the location (not what the packet sends). */
    public Block getBlock()          { return location.getBlock(); }

    @Override public boolean isCancelled()           { return cancelled; }
    @Override public void setCancelled(boolean c)    { this.cancelled = c; }

    @Override public HandlerList getHandlers()       { return HANDLERS; }
    public static HandlerList getHandlerList()       { return HANDLERS; }

    // ── Listener PacketEvents ──

    private static boolean listenerRegistered = false;

    public static synchronized void registerListener() {
        if (listenerRegistered) return;
        listenerRegistered = true;

        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract() {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                if (HANDLERS.getRegisteredListeners().length == 0) return;
                boolean single = event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE;
                boolean multi = event.getPacketType() == PacketType.Play.Server.MULTI_BLOCK_CHANGE;
                if (!single && !multi) return;
                if (!(event.getPlayer() instanceof Player player)) return;

                if (single) {
                    WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(event);
                    Vector3i pos = wrapper.getBlockPosition();
                    if (call(player, pos.getX(), pos.getY(), pos.getZ(), wrapper.getBlockState())) {
                        event.setCancelled(true);
                    }
                    return;
                }

                WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(event);
                EncodedBlock[] blocks = wrapper.getBlocks();
                List<EncodedBlock> kept = new ArrayList<>(blocks.length);
                for (EncodedBlock block : blocks) {
                    WrappedBlockState state = WrappedBlockState.getByGlobalId(block.getBlockId());
                    if (!call(player, block.getX(), block.getY(), block.getZ(), state)) {
                        kept.add(block);
                    }
                }
                if (kept.size() == blocks.length) return;
                if (kept.isEmpty()) {
                    event.setCancelled(true);
                } else {
                    wrapper.setBlocks(kept.toArray(new EncodedBlock[0]));
                    event.markForReEncode(true);
                }
            }
        });
    }

    /** @return true if a script cancelled the change */
    private static boolean call(Player player, int x, int y, int z, WrappedBlockState state) {
        BlockData data = SpigotConversionUtil.toBukkitBlockData(state);
        BlockChangePacket bukkitEvent = new BlockChangePacket(player, new Location(player.getWorld(), x, y, z), data);
        Bukkit.getPluginManager().callEvent(bukkitEvent);
        return bukkitEvent.isCancelled();
    }
}
