package skwhy.quest.npc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import skwhy.modules.QuestElements.events.QuestDialogueAnswerEvent;
import skwhy.modules.QuestElements.events.QuestNpcNameLearnEvent;
import skwhy.quest.data.PlayerQuestData;
import skwhy.quest.data.QuestStore;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.script.QuestText;

/**
 * Messages de NPC et réponses cliquables. Les réponses utilisent des rappels de clic (pas de commande) : un message
 * obsolète, expiré, trop éloigné ou d'un autre monde n'est plus accepté.
 */
public final class DialogueService {

    /** Une réponse proposée au joueur. */
    public record Answer(String text, String hover, Consumer<Player> handler) {
        public Answer(String text, Consumer<Player> handler) {
            this(text, null, handler);
        }
    }

    /** Réglages d'une série de réponses. */
    public static final class Options {
        /** Distance maximale entre le joueur et l'entité du NPC (négatif : aucune limite). */
        public double distance = -1;
        /** Durée de validité en secondes (négatif : 30 minutes). */
        public double seconds = -1;
        /** {@code true} : réponses sur une même ligne ; sinon une réponse par ligne. */
        public boolean inline;
        public Entity entity;
    }

    private record Session(UUID token, UUID entity, String world, long expires, double distance, List<Answer> answers, QuestNpc npc) {
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private DialogueService() {}

    /** Affiche un message de NPC au joueur, suivi des réponses éventuelles. */
    public static void say(Player player, QuestNpc speaker, String text, List<Answer> answers, Options options) {
        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        Component name = nameComponent(player, speaker, text, data);
        Component message = QuestText.component(text, player).colorIfAbsent(TextColor.color(57, 214, 171));
        Component line = Component.newline()
            .append(Component.text("[", NamedTextColor.GRAY)).append(name).append(Component.text("] ", NamedTextColor.GRAY))
            .append(message);

        SESSIONS.remove(player.getUniqueId());
        if (answers != null && !answers.isEmpty()) {
            Options o = options == null ? new Options() : options;
            UUID token = UUID.randomUUID();
            long expires = o.seconds >= 0 ? System.currentTimeMillis() + (long) (o.seconds * 1000) : Long.MAX_VALUE;
            Session session = new Session(token, o.entity == null ? null : o.entity.getUniqueId(),
                player.getWorld().getName(), expires, o.distance, List.copyOf(answers), speaker);
            SESSIONS.put(player.getUniqueId(), session);

            Duration lifetime = o.seconds >= 0 ? Duration.ofMillis((long) (o.seconds * 1000) + 1000) : Duration.ofMinutes(30);
            List<Component> options2 = new ArrayList<>();
            for (int i = 0; i < answers.size(); i++) {
                Answer answer = answers.get(i);
                int index = i;
                Component option = Component.text("[", NamedTextColor.YELLOW)
                    .append(QuestText.component(answer.text(), player).colorIfAbsent(NamedTextColor.YELLOW))
                    .append(Component.text("]", NamedTextColor.YELLOW))
                    .clickEvent(ClickEvent.callback(
                        audience -> click(player.getUniqueId(), token, index),
                        builder -> builder.uses(ClickCallback.UNLIMITED_USES).lifetime(lifetime)));
                if (answer.hover() != null) option = option.hoverEvent(HoverEvent.showText(QuestText.component(answer.hover(), player)));
                options2.add(option);
            }
            line = line.append(Component.newline()).append(Component.join(
                net.kyori.adventure.text.JoinConfiguration.separator(o.inline ? Component.space() : Component.newline()), options2));
        }
        player.sendMessage(line);
    }

    /** Affiche le nom du NPC, ou « ??? » tant que le joueur ne le connaît pas. */
    private static Component nameComponent(Player player, QuestNpc speaker, String text, PlayerQuestData data) {
        boolean known = speaker.nameVisible() || data.knownNames.contains(speaker.id());
        if (!known && speaker.name() != null) {
            String plainName = PlainTextComponentSerializer.plainText().serialize(QuestText.component(speaker.name(), player));
            String plainText = PlainTextComponentSerializer.plainText().serialize(QuestText.component(text, player));
            if (!plainName.isEmpty() && plainText.contains(plainName)) {
                data.knownNames.add(speaker.id());
                data.markDirty();
                known = true;
                Bukkit.getPluginManager().callEvent(new QuestNpcNameLearnEvent(player, speaker));
            }
        }
        if (!known) return Component.text(" ??? ", NamedTextColor.AQUA, TextDecoration.BOLD);
        Component name = QuestText.component(speaker.displayName(), player);
        if (speaker.name() == null || !speaker.name().contains("<") && !speaker.name().contains("&")) {
            name = name.colorIfAbsent(NamedTextColor.AQUA).decorate(TextDecoration.BOLD);
        } else {
            name = name.decorate(TextDecoration.BOLD);
        }
        if (speaker.hover() != null) name = name.hoverEvent(HoverEvent.showText(QuestText.component(speaker.hover(), player)));
        return name;
    }

    private static void click(UUID playerId, UUID token, int index) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;
        Session session = SESSIONS.get(playerId);
        if (session == null || !session.token.equals(token)) {
            player.playSound(player.getLocation(), Sound.BLOCK_WOOL_HIT, 1f, 1f);
            player.sendMessage(Component.text("Vous ne pouvez plus répondre à ce message.", NamedTextColor.RED));
            return;
        }
        World world = player.getWorld();
        if (!world.getName().equals(session.world)) {
            SESSIONS.remove(playerId);
            player.sendMessage(Component.text("Vous avez changé de monde.", NamedTextColor.RED));
            return;
        }
        if (System.currentTimeMillis() > session.expires) {
            SESSIONS.remove(playerId);
            player.sendMessage(Component.text("Le délais de réponse a expiré.", NamedTextColor.RED));
            return;
        }
        if (session.distance >= 0 && session.entity != null) {
            Entity entity = Bukkit.getEntity(session.entity);
            if (entity == null || !entity.getWorld().equals(world) || entity.getLocation().distance(player.getLocation()) > session.distance) {
                SESSIONS.remove(playerId);
                player.sendMessage(Component.text("Vous vous êtes trop éloigné.", NamedTextColor.RED));
                return;
            }
        }
        if (index < 0 || index >= session.answers.size()) return;
        SESSIONS.remove(playerId);
        Answer answer = session.answers.get(index);
        QuestDialogueAnswerEvent event = new QuestDialogueAnswerEvent(player, session.npc, answer.text());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        answer.handler().accept(player);
    }

    /** Annule les réponses en attente d'un joueur. */
    public static void clear(UUID player) {
        SESSIONS.remove(player);
    }

    public static void clearAll() {
        SESSIONS.clear();
    }
}
