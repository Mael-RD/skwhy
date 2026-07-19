package skwhy.modules.RandomStuff.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
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
import com.github.retrooper.packetevents.protocol.advancements.AdvancementProgress.CriterionProgress;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementType;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAdvancements;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Name("Send Notification")
@Description({
    "Sends a fake advancement toast notification to one or more players.",
    "100% packet-based via PacketEvents: no advancement is ever registered in, or",
    "removed from, the server's advancement registry, and no Bukkit advancement API",
    "is used at all. A single WrapperPlayServerUpdateAdvancements packet (reset=false)",
    "is sent directly to each player with the fake advancement already marked as",
    "obtained, which triggers the toast. A second packet then tells the client to",
    "forget the fake advancement.",
    "Only the title is shown: vanilla toasts never display the advancement description,",
    "so it has been dropped from this syntax entirely.",
    "The type controls the frame: 'task' = normal, 'goal' = rounded, 'challenge' = challenge frame."
})
@Examples({
    "send task notification with item diamond titled \"New Task\" to player",
    "send challenge notification with item nether_star titled \"Challenge!\" to all players",
    "send goal notification with item gold_ingot titled \"Goal\" to {_players::*}"
})
@Since("1.5.0")
public class SendNotification extends Effect {

    private static final AdvancementType[] TYPES = {
        AdvancementType.TASK, AdvancementType.GOAL, AdvancementType.CHALLENGE
    };

    // Délai (en ticks) avant l'envoi du paquet de suppression côté client.
    // Purement cosmétique : aucune trace ne reste jamais côté serveur de toute façon.
    private static final long CLEANUP_DELAY_TICKS = 60L;

    private Expression<ItemStack> exprItem;
    private Expression<String>    exprTitle;
    private Expression<Player>    exprPlayers;
    private int                   matchedType;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, ParseResult pr) {
        this.matchedType = matchedPattern;
        this.exprItem     = (Expression<ItemStack>) exprs[0];
        this.exprTitle    = (Expression<String>)    exprs[1];
        this.exprPlayers  = (Expression<Player>)    exprs[2];
        return true;
    }

    @Override
    protected void execute(Event event) {
        ItemStack  bukkitItem    = exprItem.getSingle(event);
        String     title         = exprTitle.getSingle(event);
        Player[]   targetPlayers = exprPlayers.getAll(event);

        if (bukkitItem == null || title == null
                || targetPlayers == null || targetPlayers.length == 0) return;

        Plugin plugin = Bukkit.getPluginManager().getPlugin("skwhy");
        if (plugin == null) return;

        AdvancementType type     = TYPES[matchedType];
        String          uniqueId = "notif_" + UUID.randomUUID().toString().replace("-", "");
        ResourceLocation key      = new ResourceLocation("skwhy", uniqueId);

        // ItemStack Bukkit -> ItemStack PacketEvents pour l'icône du toast.
        com.github.retrooper.packetevents.protocol.item.ItemStack peIcon =
            SpigotConversionUtil.fromBukkitItemStack(bukkitItem);

        // Component.text = texte brut, sans parsing de couleurs (comportement
        // identique à l'ancienne version qui posait directement la String en JSON).
        // Description vide : le toast vanilla ne l'affiche jamais, seul le titre compte.
        AdvancementDisplay display = new AdvancementDisplay(
            Component.text(title),
            Component.empty(),
            peIcon,
            type,
            null,     // pas de background custom
            true,     // showToast
            true,     // hidden (invisible dans l'onglet advancements)
            0f, 0f    // x / y (position dans l'arbre, sans effet ici : hidden=true)
        );

        // Un seul critère bidon "done", jamais réellement déclenchable côté serveur
        // puisque l'advancement n'existe jamais réellement sur le serveur : on
        // l'envoie déjà comme "obtenu" directement dans le paquet de progression.
        List<List<String>> requirements = List.of(List.of("done"));

        Advancement advancement = new Advancement(
            null,           // pas de parent -> advancement root autonome
            display,
            requirements,
            false           // sendsTelemetryData
        );

        AdvancementHolder holder = new AdvancementHolder(key, advancement);

        AdvancementProgress progress = new AdvancementProgress(
            Map.of("done", new CriterionProgress(System.currentTimeMillis()))
        );

        // reset=false : paquet incrémental, condition indispensable pour que le
        // client affiche le toast (un paquet reset=true ne l'affiche jamais).
        WrapperPlayServerUpdateAdvancements addPacket = new WrapperPlayServerUpdateAdvancements(
            false,
            List.of(holder),
            Set.of(),
            Map.of(key, progress),
            true // showAdvancements (ajouté en 1.21.5, ignoré sur les versions plus anciennes)
        );

        WrapperPlayServerUpdateAdvancements removePacket = new WrapperPlayServerUpdateAdvancements(
            false,
            List.of(),
            Set.of(key),
            Map.of(),
            true
        );

        for (Player player : targetPlayers) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, addPacket);
        }

        // Nettoyage côté client uniquement : aucun registre serveur n'a jamais
        // été touché, donc rien à annuler côté serveur.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : targetPlayers) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, removePacket);
            }
        }, CLEANUP_DELAY_TICKS);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "send " + TYPES[matchedType].toString().toLowerCase() + " notification"
            + " with item " + exprItem.toString(event, debug)
            + " titled " + exprTitle.toString(event, debug)
            + " to " + exprPlayers.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(SendNotification.class)
                .addPattern("send (task) notification with item %itemstack% (titled|title) %string% to %players%")
                .addPattern("send (goal) notification with item %itemstack% (titled|title) %string% to %players%")
                .addPattern("send (challenge) notification with item %itemstack% (titled|title) %string% to %players%")
                .build()
        );
    }
}