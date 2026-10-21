package skwhy.quest.npc;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import skwhy.SkWhy;
import skwhy.modules.QuestElements.events.QuestNpcInteractEvent;
import skwhy.modules.QuestElements.events.QuestNpcMeetEvent;
import skwhy.modules.QuestElements.events.QuestNpcPositionChangeEvent;
import skwhy.quest.QuestEngine;
import skwhy.quest.QuestRegistry;
import skwhy.quest.QuestSettings;
import skwhy.quest.data.PlayerQuestData;
import skwhy.quest.data.QuestStore;
import skwhy.quest.model.NpcEntityRef;
import skwhy.quest.model.QuestNpc;
import skwhy.quest.script.ItemResolver;
import skwhy.quest.script.ScriptContext;
import skwhy.quest.script.ScriptEngine;

/**
 * NPC du monde : interactions, position vue par chaque joueur (un même NPC peut exister à plusieurs endroits, un seul
 * est visible pour un joueur donné) et messages automatiques.
 */
public final class NpcService {

    private static final Random RANDOM = new Random();
    /** Entités masquées par nos soins, par joueur. */
    private static final Map<UUID, Set<UUID>> HIDDEN = new ConcurrentHashMap<>();
    /** NPC entièrement masqués pendant un déplacement : joueur → ids de NPC. */
    private static final Map<UUID, Set<String>> SUSPENDED = new ConcurrentHashMap<>();

    private static final List<String> GENERIC = List.of(
        "Bonjour, étranger. Le temps est clément aujourd'hui, n'est-ce pas ?",
        "Ah, un nouveau visage ! Vous cherchez quelque chose ?",
        "Je suis juste là à observer le monde passer. Rien d'excitant ne se passe jamais ici.",
        "La routine quotidienne, c'est tout ce que je connais. Rien de plus, rien de moins.");

    private static final List<String> ATTACKED = List.of(
        "Aïe ! Arrête de me frapper !", "Ouïe ! Arrête ça ! Ça fait mal !", "C'est pas bien de frapper les gens. Vilain !",
        "Hey, doucement ! Je ne suis pas un punching-ball !");

    private NpcService() {}

    // ── Entités et positions ────────────────────────────────────────────────

    public static Entity entity(NpcEntityRef ref) {
        try {
            return Bukkit.getEntity(UUID.fromString(ref.entityUuid()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static NpcEntityRef refOf(Entity entity) {
        return entity == null ? null : QuestRegistry.refOf(entity.getUniqueId());
    }

    /** Position (1, 2, 3...) sous laquelle ce joueur voit le NPC. */
    public static int position(OfflinePlayer player, QuestNpc npc) {
        return QuestStore.get(player.getUniqueId()).npcPosition(npc.id());
    }

    /** Change la position du NPC pour le joueur et met à jour ce qu'il voit. */
    public static boolean setPosition(OfflinePlayer player, QuestNpc npc, int position) {
        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        int past = data.npcPosition(npc.id());
        if (past == position) return false;
        QuestNpcPositionChangeEvent event = new QuestNpcPositionChangeEvent(player, npc, past, position);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        data.npcPositions.put(npc.id(), position);
        data.markDirty();
        Player online = player.getPlayer();
        if (online != null) refreshVisibility(online);
        QuestEngine.refresh(player);
        return true;
    }

    // ── Visibilité ──────────────────────────────────────────────────────────

    public static void suspend(UUID player, String npc) {
        SUSPENDED.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(npc);
        Player online = Bukkit.getPlayer(player);
        if (online != null) refreshVisibility(online);
    }

    public static void resume(UUID player, String npc) {
        Set<String> set = SUSPENDED.get(player);
        if (set != null) set.remove(npc);
        Player online = Bukkit.getPlayer(player);
        if (online != null) refreshVisibility(online);
    }

    /** {@code true} si le joueur doit voir cette entité de NPC. */
    public static boolean shouldSee(Player player, NpcEntityRef ref) {
        // Un staff voit toutes les positions tant que le metadata "quest_see_all_npcs" est posé sur lui (ex. item de staff en main)
        if (player.hasPermission(QuestSettings.seeAllPermission()) && player.hasMetadata("quest_see_all_npcs")) return true;
        Set<String> suspended = SUSPENDED.get(player.getUniqueId());
        if (suspended != null && suspended.contains(ref.npcId())) return false;
        return QuestStore.get(player.getUniqueId()).npcPosition(ref.npcId()) == ref.position();
    }

    /** Masque ou affiche chaque entité de NPC chargée selon la position vue par le joueur. */
    public static void refreshVisibility(Player player) {
        Set<UUID> hidden = HIDDEN.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        for (NpcEntityRef ref : QuestRegistry.entityRefs()) {
            Entity entity = entity(ref);
            if (entity == null) continue;
            boolean see = shouldSee(player, ref);
            if (!see && !hidden.contains(entity.getUniqueId())) {
                player.hideEntity(SkWhy.getInstance(), entity);
                hidden.add(entity.getUniqueId());
            } else if (see && hidden.remove(entity.getUniqueId())) {
                player.showEntity(SkWhy.getInstance(), entity);
            }
        }
    }

    /** Applique la visibilité d'une entité qui vient de se charger à tous les joueurs connectés. */
    public static void refreshVisibility(Entity entity) {
        NpcEntityRef ref = refOf(entity);
        if (ref == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Set<UUID> hidden = HIDDEN.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
            if (!shouldSee(player, ref)) {
                player.hideEntity(SkWhy.getInstance(), entity);
                hidden.add(entity.getUniqueId());
            }
        }
    }

    public static void forget(UUID player) {
        HIDDEN.remove(player);
        SUSPENDED.remove(player);
    }

    // ── Interaction ─────────────────────────────────────────────────────────

    /** @return {@code true} si l'entité est un NPC et que l'interaction a été prise en charge */
    public static boolean interact(Player player, Entity entity, ClickType click) {
        NpcEntityRef ref = refOf(entity);
        if (ref == null) return false;
        QuestNpc npc = QuestRegistry.npc(ref.npcId());
        if (npc == null) return false;

        QuestNpcInteractEvent event = new QuestNpcInteractEvent(player, npc, entity, click);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return true;

        if (click == ClickType.LEFT) {
            if (entity instanceof Mannequin) {
                List<String> texts = npc.attackTexts();
                String text = texts.isEmpty() ? ATTACKED.get(RANDOM.nextInt(ATTACKED.size())) : texts.get(RANDOM.nextInt(texts.size()));
                DialogueService.say(player, npc, text, null, null);
            }
            return true;
        }

        PlayerQuestData data = QuestStore.get(player.getUniqueId());
        if (data.metNpcs.add(npc.id())) {
            data.markDirty();
            Bukkit.getPluginManager().callEvent(new QuestNpcMeetEvent(player, npc));
            awardMeetAdvancements(player);
        }
        if (npc.meetMessage() != null && !data.knownNames.contains(npc.id())) {
            DialogueService.say(player, npc, npc.meetMessage(), null, null);
            return true;
        }
        String type = npc.type();
        if (type == null) return true;
        switch (type) {
            case "discour" -> {
                List<String> texts = npc.texts();
                String text = texts.isEmpty() ? GENERIC.get(RANDOM.nextInt(GENERIC.size())) : texts.get(RANDOM.nextInt(texts.size()));
                DialogueService.say(player, npc, text, null, null);
            }
            case "marchand" -> {
                if (!npc.trade().isEmpty()) {
                    TradeService.open(player, npc.trade(), ScriptContext.forNpc(player, npc, entity));
                    if (!npc.texts().isEmpty()) {
                        DialogueService.say(player, npc, npc.texts().get(RANDOM.nextInt(npc.texts().size())), null, null);
                    }
                }
            }
            case "scripté", "scripte", "script" -> ScriptEngine.run(ScriptContext.forNpc(player, npc, entity), "main", null);
            default -> { }
        }
        return true;
    }

    private static void awardMeetAdvancements(Player player) {
        List<String> keys = SkWhy.getInstance().getConfig().getStringList("quests.meet_advancements");
        for (String key : keys) {
            NamespacedKey nk = NamespacedKey.fromString(key);
            Advancement advancement = nk == null ? null : Bukkit.getAdvancement(nk);
            if (advancement == null) continue;
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            Set<String> remaining = new HashSet<>(progress.getRemainingCriteria());
            if (!remaining.isEmpty()) progress.awardCriteria(remaining.iterator().next());
        }
    }

    /** Un item de la récompense ou d'un échange, pour les scripts qui en ont besoin. */
    public static org.bukkit.inventory.ItemStack item(Map<String, Object> entry, OfflinePlayer player) {
        return ItemResolver.read(entry, player);
    }
}
