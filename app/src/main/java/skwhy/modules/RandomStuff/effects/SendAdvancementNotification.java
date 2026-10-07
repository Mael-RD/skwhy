package skwhy.modules.RandomStuff.effects;

import ch.njol.skript.aliases.ItemType;
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
import com.github.retrooper.packetevents.protocol.advancements.Advancement;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementDisplay;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementHolder;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementProgress;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementType;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAdvancements;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Name("Send Advancement Notification")
@Description("Sends a task, goal, or challenge advancement toast to players using packets only. " +
        "The advancement is hidden from advancement tabs and never produces a chat message.")
@Examples({
    "send task notification with item diamond title \"A notification\" to all players",
    "send challenge notification with item nether star title \"Challenge complete!\" to player"
})
@Since("1.4.0")
@RequiredPlugins("PacketEvents")
public class SendAdvancementNotification extends Effect {

    private static final ResourceLocation HIDDEN_PARENT = new ResourceLocation("minecraft:recipes/root");
    private static final String IMPOSSIBLE_CRITERION = "impossible";

    private Expression<ItemType> item;
    private Expression<String> title;
    private Expression<Player> players;
    private AdvancementType advancementType;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, ParseResult pr) {
        this.item = (Expression<ItemType>) exprs[0];
        this.title = (Expression<String>) exprs[1];
        this.players = (Expression<Player>) exprs[2];
        this.advancementType = switch (matchedPattern) {
            case 1 -> AdvancementType.GOAL;
            case 2 -> AdvancementType.CHALLENGE;
            default -> AdvancementType.TASK;
        };
        return true;
    }

    @Override
    protected void execute(Event event) {
        ItemType itemType = item.getSingle(event);
        String notificationTitle = title.getSingle(event);
        Player[] targetPlayers = players.getAll(event);
        if (itemType == null || notificationTitle == null || targetPlayers == null) return;

        ItemStack icon = itemType.getRandom();
        if (icon == null || icon.isEmpty()) return;

        for (Player player : targetPlayers) {
            var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user == null) continue;

            ResourceLocation advancementId = new ResourceLocation("skwhy:" + UUID.randomUUID());
            AdvancementDisplay display = new AdvancementDisplay(
                    Component.text(notificationTitle),
                    Component.empty(),
                    SpigotConversionUtil.fromBukkitItemStack(icon),
                    advancementType,
                    null,
                    true,
                    true,
                    0.0f,
                    0.0f
            );
            Advancement advancement = new Advancement(
                    HIDDEN_PARENT,
                    display,
                    List.of(List.of(IMPOSSIBLE_CRITERION)),
                    false
            );
            AdvancementHolder holder = new AdvancementHolder(advancementId, advancement);
            AdvancementProgress progress = new AdvancementProgress(Map.of(
                    IMPOSSIBLE_CRITERION,
                    new AdvancementProgress.CriterionProgress(System.currentTimeMillis())
            ));

            user.sendPacket(new WrapperPlayServerUpdateAdvancements(
                    false,
                    List.of(holder),
                    Set.of(),
                    Map.of(advancementId, progress),
                    true
            ));
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "send " + advancementType.name().toLowerCase() + " notification with item "
                + item.toString(event, debug) + " title " + title.toString(event, debug)
                + " to " + players.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
                SyntaxRegistry.EFFECT,
                SyntaxInfo.builder(SendAdvancementNotification.class)
                        .addPattern("send task notification with item %item% title %string% to %players%")
                        .addPattern("send goal notification with item %item% title %string% to %players%")
                        .addPattern("send challenge notification with item %item% title %string% to %players%")
                        .build()
        );
    }
}
