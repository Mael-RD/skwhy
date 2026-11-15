package skwhy.quest.npc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import skwhy.customentity.CustomEntity;
import skwhy.customentity.CustomEntityManager;
import skwhy.quest.QuestRegistry;
import skwhy.quest.model.NpcEntityRef;

/**
 * Pont entre les NPC de quêtes et les entités custom ({@code skwhy.customentity}) : une entité custom <b>réelle</b> est une
 * interaction du serveur, liée comme n'importe quelle entité (tag NBT) ; une entité custom <b>fausse</b> n'existe que par paquets,
 * elle est liée en mémoire (clé = l'UUID de son interaction fausse) et ne survit pas au redémarrage, comme elle.
 * Les clics sur le visuel ou l'interaction des deux arrivent par {@code CustomEntityInteractEvent}.
 */
public final class CustomNpcBodies {

    /** Entités custom fausses liées à un NPC : UUID de leur interaction → entité. */
    private static final Map<UUID, CustomEntity> FAKES = new ConcurrentHashMap<>();

    private CustomNpcBodies() {}

    // ── Liaison d'une entité custom ─────────────────────────────────────────

    /** Lie une entité custom (réelle ou fausse) à un NPC à une position et enregistre l'endroit de cette position. */
    public static NpcEntityRef bind(CustomEntity entity, String npcId, int position) {
        if (entity.isReal()) {
            Entity real = CustomEntityManager.get() == null ? null : CustomEntityManager.get().realEntity(entity);
            return real == null ? null : QuestRegistry.bind(real, npcId, position);
        }
        Location at = entity.fakeLocation();
        if (at == null) return null;
        NpcEntityRef ref = new NpcEntityRef(npcId, entity.hitboxUuid().toString(), position,
            at.getWorld() == null ? "" : at.getWorld().getName(), at.getX(), at.getY(), at.getZ());
        FAKES.put(entity.hitboxUuid(), entity);
        QuestRegistry.bindEntity(entity.hitboxUuid(), ref);
        NpcPlaces.set(npcId, position, NpcPlaces.Place.of(at));
        NpcService.refreshAll();
        return ref;
    }

    /** @return {@code true} si l'entité était liée */
    public static boolean unbind(CustomEntity entity) {
        if (entity.isReal()) {
            Entity real = CustomEntityManager.get() == null ? null : CustomEntityManager.get().realEntity(entity);
            return real != null && QuestRegistry.unbind(real);
        }
        boolean had = FAKES.remove(entity.hitboxUuid()) != null;
        QuestRegistry.unbindEntity(entity.hitboxUuid());
        if (had) NpcService.refreshAll();
        return had;
    }

    /** Le NPC que représente une entité custom, ou {@code null}. */
    public static NpcEntityRef refOf(CustomEntity entity) {
        if (entity == null) return null;
        if (entity.isFake()) return entity.isDestroyed() ? null : QuestRegistry.refOf(entity.hitboxUuid());
        Entity real = CustomEntityManager.get() == null ? null : CustomEntityManager.get().realEntity(entity);
        return real == null ? null : QuestRegistry.refOf(real);
    }

    // ── Corps fictifs ───────────────────────────────────────────────────────

    /** L'entité custom fausse qui sert de corps à cette référence, ou {@code null}. */
    public static CustomEntity fake(NpcEntityRef ref) {
        try {
            return FAKES.get(UUID.fromString(ref.entityUuid()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isFake(NpcEntityRef ref) {
        return fake(ref) != null;
    }

    /** Oublie les entités fausses détruites (et leur liaison). */
    public static void purge() {
        if (FAKES.isEmpty()) return;
        for (Map.Entry<UUID, CustomEntity> e : FAKES.entrySet()) {
            if (!e.getValue().isDestroyed()) continue;
            FAKES.remove(e.getKey());
            QuestRegistry.unbindEntity(e.getKey());
        }
    }

    /** {@code true} si cette interaction réelle porte un id d'entité custom (ses clics passent par l'évènement des entités custom). */
    public static boolean isCustom(Entity entity) {
        CustomEntityManager manager = CustomEntityManager.get();
        return manager != null && manager.idOf(entity) != null;
    }

    /** Id de packet du visuel d'une entité réelle qui est une entité custom, {@code -1} sinon. */
    public static int visualId(Entity entity) {
        CustomEntityManager manager = CustomEntityManager.get();
        CustomEntity handle = manager == null ? null : manager.handleOf(entity);
        return handle == null ? -1 : handle.visualId();
    }

    /** Pose le filtre de visibilité : une entité custom fausse d'un NPC n'est envoyée qu'aux joueurs qui voient cette position. */
    public static void install() {
        CustomEntityManager.visibilityFilter((entity, player) -> {
            NpcEntityRef ref = entity.isFake() ? QuestRegistry.refOf(entity.hitboxUuid()) : null;
            return ref == null || NpcService.shouldSee(player, ref);
        });
    }

    public static void uninstall() {
        CustomEntityManager.visibilityFilter(null);
        FAKES.clear();
    }

    /** Réapplique la visibilité aux entités fausses. */
    public static void refreshViewers() {
        CustomEntityManager manager = CustomEntityManager.get();
        if (manager != null) manager.refreshVisibility();
    }
}
