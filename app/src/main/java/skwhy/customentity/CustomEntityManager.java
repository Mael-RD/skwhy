package skwhy.customentity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import com.github.retrooper.packetevents.PacketEvents;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import skwhy.SkWhy;
import skwhy.modules.CustomEntityElements.events.CustomEntityInteractEvent;

/**
 * Gère les entités custom. Une entité réelle est une interaction du serveur dont les NBT ne contiennent que l'id de la
 * définition ({@code BukkitValues} → {@code skwhy:custom_entity}) ; une entité fausse n'existe que par packets. Dans les deux cas
 * le visuel (type d'entité, nom, équipement...) est décrit par la définition et envoyé par packet aux joueurs proches.
 */
public final class CustomEntityManager implements Listener {

    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type OVERRIDES_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private static final int REFRESH_TICKS = 10;
    /** Marge au-delà de la distance de vue avant de retirer une entité fausse à un joueur. */
    private static final double HIDE_MARGIN = 8.0;

    private static CustomEntityManager instance;

    private final SkWhy plugin;
    private final NamespacedKey key;
    /** Surcharges d'une entité réelle (JSON), à côté de son id. */
    private final NamespacedKey overridesKey;
    private final CustomEntityRegistry registry;
    private final Map<UUID, CustomEntity> reals = new ConcurrentHashMap<>();
    private final Set<CustomEntity> fakes = ConcurrentHashMap.newKeySet();
    /** Mobs vanilla qui pilotent les entités custom ayant une IA. */
    private final Map<CustomEntity, CustomEntityBrain> brains = new ConcurrentHashMap<>();
    /** Ids de packet (visuels, interactions fausses, entités réelles) → entité custom, pour les clics des joueurs. */
    private final Map<Integer, CustomEntity> byPacketId = new ConcurrentHashMap<>();
    private BukkitTask task;
    private PacketListenerAbstract packetListener;
    private long ticks;

    private CustomEntityManager(SkWhy plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "custom_entity");
        this.overridesKey = new NamespacedKey(plugin, "custom_entity_overrides");
        this.registry = new CustomEntityRegistry(new File(plugin.getDataFolder(), plugin.getConfig().getString("custom_entity.folder", "custom-entities")), plugin.getLogger());
    }

    public static void init(SkWhy plugin) {
        if (instance != null) shutdown();
        instance = new CustomEntityManager(plugin);
        instance.registry.reload();
        Bukkit.getPluginManager().registerEvents(instance, plugin);
        instance.registerPacketListener();
        instance.task = Bukkit.getScheduler().runTaskTimer(plugin, instance::tick, 1L, 1L);
        // les entités déjà chargées (rechargement du plugin) : sans cela leur IA ne tourne qu'à la prochaine charge de chunk
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (instance == null) return;
            for (World world : Bukkit.getWorlds()) {
                for (Interaction interaction : world.getEntitiesByClass(Interaction.class)) instance.handleOf(interaction);
            }
        });
    }

    public static void shutdown() {
        CustomEntityManager m = instance;
        if (m == null) return;
        instance = null;
        if (m.task != null) m.task.cancel();
        if (m.packetListener != null) PacketEvents.getAPI().getEventManager().unregisterListener(m.packetListener);
        org.bukkit.event.HandlerList.unregisterAll(m);
        for (CustomEntity e : new ArrayList<>(m.reals.values())) m.hideAll(e);
        for (CustomEntity e : new ArrayList<>(m.fakes)) m.hideAll(e);
        m.reals.clear();
        m.fakes.clear();
        m.brains.clear();
        m.byPacketId.clear();
    }

    public static CustomEntityManager get() { return instance; }

    public CustomEntityRegistry registry() { return registry; }

    // ── Création, identifiants ──────────────────────────────────────────────

    /** Crée une entité réelle : une interaction qui porte l'id. {@code null} si l'id n'est pas défini. */
    public CustomEntity spawnReal(String id, Location at) {
        return spawnReal(id, at, Map.of());
    }

    /**
     * Crée une entité réelle avec des surcharges (chemin YAML → valeur) qui l'emportent sur la définition ; elles sont écrites avec
     * l'id dans l'interaction. Une surcharge invalide est ignorée (avertissement dans la console).
     */
    public CustomEntity spawnReal(String id, Location at, Map<String, Object> overrides) {
        CustomEntityDef base = registry.get(id);
        if (base == null || at.getWorld() == null) return null;
        Map<String, Object> clean = sanitize(base.id(), overrides);
        CustomEntityDef def = base.withOverrides(clean, new ArrayList<>());
        at.getWorld().getChunkAt(at); // charge le chunk : sans joueur à proximité l'entité serait refusée
        Interaction interaction = at.getWorld().spawn(at, Interaction.class, spawned -> {
            spawned.setInteractionWidth((float) def.hitboxWidth());
            spawned.setInteractionHeight((float) def.hitboxHeight());
            spawned.setPersistent(true);
            spawned.getPersistentDataContainer().set(key, PersistentDataType.STRING, base.id());
            if (!clean.isEmpty()) spawned.getPersistentDataContainer().set(overridesKey, PersistentDataType.STRING, GSON.toJson(clean));
        });
        if (!interaction.isValid()) return null; // le serveur a refusé l'entité (chunk non chargé, évènement annulé...)
        return handleOf(interaction);
    }

    /** Crée une entité fausse (packets uniquement). {@code null} si l'id n'est pas défini. */
    public CustomEntity spawnFake(String id, Location at) {
        return spawnFake(id, at, Map.of());
    }

    /** Crée une entité fausse avec des surcharges (voir {@link #spawnReal(String, Location, Map)}). */
    public CustomEntity spawnFake(String id, Location at, Map<String, Object> overrides) {
        CustomEntityDef def = registry.get(id);
        if (def == null || at.getWorld() == null) return null;
        CustomEntity entity = new CustomEntity(def.id(), at);
        entity.overrides(sanitize(def.id(), overrides));
        fakes.add(entity);
        byPacketId.put(entity.visualId(), entity);
        byPacketId.put(entity.hitboxId(), entity);
        refreshViewers(entity);
        return entity;
    }

    /** Ne garde que les surcharges valides (les autres sont signalées dans la console). */
    private Map<String, Object> sanitize(String id, Map<String, Object> overrides) {
        Map<String, Object> clean = new LinkedHashMap<>();
        overrides.forEach((path, value) -> {
            String problem = value == null ? "valeur manquante" : checkOverride(path, value);
            if (problem == null) clean.put(path, value);
            else plugin.getLogger().warning("[CustomEntity] " + id + " : surcharge ignorée : " + problem);
        });
        return clean;
    }

    private static String checkOverride(String path, Object value) {
        if (path.equals("type") && value instanceof String type
                && EntityTypes.getByName("minecraft:" + type.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "")) == null) {
            return "type d'entité inconnu '" + type + "'";
        }
        return CustomEntityDef.checkOverride(path, value);
    }

    /** L'id de définition porté par une entité, ou {@code null}. */
    public String idOf(Entity entity) {
        if (!(entity instanceof Interaction)) return null;
        return entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /** L'entité custom d'une interaction qui porte un id, ou {@code null}. */
    public CustomEntity handleOf(Entity entity) {
        String id = idOf(entity);
        if (id == null) return null;
        CustomEntity handle = reals.computeIfAbsent(entity.getUniqueId(), uuid -> {
            CustomEntity created = new CustomEntity(id, entity);
            created.overrides(readOverrides(entity));
            byPacketId.put(created.visualId(), created);
            byPacketId.put(created.hitboxId(), created);
            return created;
        });
        handle.definitionId(id);
        return handle;
    }

    private Map<String, Object> readOverrides(Entity entity) {
        String json = entity.getPersistentDataContainer().get(overridesKey, PersistentDataType.STRING);
        if (json == null) return Map.of();
        try {
            Map<String, Object> map = GSON.fromJson(json, OVERRIDES_TYPE);
            return map == null ? Map.of() : map;
        } catch (RuntimeException e) {
            plugin.getLogger().warning("[CustomEntity] surcharges illisibles sur " + entity.getUniqueId() + " : " + e.getMessage());
            return Map.of();
        }
    }

    /**
     * Pose (ou retire avec {@code null}) l'id d'une interaction réelle. Retourne {@code false} pour une autre entité ou un
     * id non défini.
     */
    public boolean setId(Entity entity, String id) {
        if (!(entity instanceof Interaction interaction)) return false;
        if (id == null) {
            interaction.getPersistentDataContainer().remove(key);
            CustomEntity handle = reals.remove(entity.getUniqueId());
            if (handle != null) forget(handle);
            return true;
        }
        CustomEntityDef def = registry.get(id);
        if (def == null) return false;
        interaction.getPersistentDataContainer().set(key, PersistentDataType.STRING, def.id());
        CustomEntity handle = handleOf(entity);
        resizeHitbox(handle);
        rerender(handle);
        return true;
    }

    /** Change la définition d'une entité (réelle ou fausse). */
    public boolean setId(CustomEntity entity, String id) {
        if (entity.isReal()) return setId(Bukkit.getEntity(entity.realUuid()), id);
        CustomEntityDef def = id == null ? null : registry.get(id);
        if (def == null) return false;
        entity.definitionId(def.id());
        resizeHitbox(entity);
        rerender(entity);
        return true;
    }

    public Entity realEntity(CustomEntity entity) {
        return entity.isReal() ? Bukkit.getEntity(entity.realUuid()) : null;
    }

    public Location locationOf(CustomEntity entity) {
        if (entity.isFake()) return entity.fakeLocation();
        Entity real = realEntity(entity);
        return real == null ? null : real.getLocation();
    }

    public void teleport(CustomEntity entity, Location to) {
        if (entity.isDestroyed() || to.getWorld() == null) return;
        if (entity.isReal()) {
            Entity real = realEntity(entity);
            if (real != null) real.teleport(to);
            return;
        }
        entity.fakeLocation(to);
        entity.markSent(to);
        refreshViewers(entity);
        for (Player viewer : viewers(entity)) CustomEntityPackets.send(viewer, CustomEntityPackets.move(entity, to));
    }

    public List<CustomEntity> fakes() { return new ArrayList<>(fakes); }

    /** Supprime l'entité : l'interaction réelle est retirée du monde, l'entité fausse disparaît chez tous les joueurs. */
    public void destroy(CustomEntity entity) {
        if (entity.isDestroyed()) return;
        Entity real = realEntity(entity);
        forget(entity);
        if (entity.isReal()) reals.remove(entity.realUuid());
        if (real != null) real.remove();
    }

    /** Relit les fichiers et met à jour ce que voient les joueurs. Retourne le nombre de définitions. */
    public int reload() {
        int count = registry.reload();
        for (CustomEntity e : new ArrayList<>(reals.values())) rerender(e);
        for (CustomEntity e : new ArrayList<>(fakes)) rerender(e);
        return count;
    }

    // ── Édition des définitions ─────────────────────────────────────────────

    /** Valeur d'un paramètre d'une définition (chemin YAML, voir {@link CustomEntityDef#value}), ou {@code null}. */
    public Object param(String id, String path) {
        CustomEntityDef def = registry.get(id);
        return def == null ? null : def.value(path);
    }

    /**
     * Écrit un paramètre dans le YAML de la définition puis met à jour les entités qui l'utilisent. {@code false} si la définition
     * est inconnue ou la valeur refusée (rien n'est alors écrit).
     */
    public boolean setParam(String id, String path, Object value) {
        if (path.equals("type") && EntityTypes.getByName("minecraft:" + String.valueOf(value).trim().toLowerCase(Locale.ROOT)) == null) return false;
        boolean done = registry.edit(id, body -> CustomEntityDef.setPath(body, path, value));
        if (done) refreshDefinition(id);
        return done;
    }

    /** Retire un paramètre : il reprend sa valeur par défaut. Le {@code type} ne peut pas être retiré. */
    public boolean deleteParam(String id, String path) {
        if (path.equals("type")) return false;
        boolean done = registry.edit(id, body -> CustomEntityDef.removePath(body, path));
        if (done) refreshDefinition(id);
        return done;
    }

    public boolean createDefinition(String id, String type) {
        if (EntityTypes.getByName("minecraft:" + type.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "")) == null) return false;
        return registry.create(id, type);
    }

    /** Supprime la définition : les entités qui l'utilisent n'ont plus de visuel jusqu'à ce qu'elle soit recréée. */
    public boolean deleteDefinition(String id) {
        boolean done = registry.remove(id);
        if (done) refreshDefinition(id);
        return done;
    }

    /** Renvoie le visuel (et la taille de la hitbox) de toutes les entités qui utilisent cette définition. */
    private void refreshDefinition(String id) {
        String normalized = CustomEntityRegistry.normalize(id);
        for (CustomEntity e : all()) {
            if (!e.definitionId().equals(normalized)) continue;
            resizeHitbox(e);
            rerender(e);
        }
    }

    private void forget(CustomEntity entity) {
        hideAll(entity);
        discardBrain(entity);
        entity.markDestroyed();
        fakes.remove(entity);
        byPacketId.remove(entity.visualId());
        byPacketId.remove(entity.hitboxId());
    }

    // ── Surcharges par entité ───────────────────────────────────────────────

    /** Définition de l'entité : celle du fichier avec ses surcharges, ou {@code null} si l'id n'est plus défini. */
    public CustomEntityDef defOf(CustomEntity entity) {
        CustomEntityDef base = registry.get(entity.definitionId());
        return base == null ? null : entity.effective(base);
    }

    /** Valeur effective d'un paramètre (surcharge, sinon définition), ou {@code null}. */
    public Object param(CustomEntity entity, String path) {
        CustomEntityDef def = defOf(entity);
        return def == null ? null : def.value(path);
    }

    /**
     * Surcharge un paramètre pour cette seule entité ; la valeur est un {@code String}, un {@code Number} ou un {@code Boolean} selon le
     * paramètre. Réelle : la surcharge est écrite dans l'interaction.
     *
     * @return {@code null} si c'est fait, sinon la raison du refus
     */
    public String setParam(CustomEntity entity, String path, Object value) {
        if (entity.isDestroyed()) return "l'entité custom n'existe plus";
        Object stored = value instanceof Number n ? (Object) n.doubleValue() : value;
        String problem = checkOverride(path, stored);
        if (problem != null) return problem;
        if (entity.putOverride(path, stored)) overridesChanged(entity);
        return null;
    }

    /** Retire la surcharge d'un paramètre : l'entité reprend la valeur de sa définition. */
    public void deleteParam(CustomEntity entity, String path) {
        if (!entity.isDestroyed() && entity.removeOverride(path)) overridesChanged(entity);
    }

    /** Retire toutes les surcharges. */
    public void clearParams(CustomEntity entity) {
        if (!entity.isDestroyed() && entity.clearOverrides()) overridesChanged(entity);
    }

    private void overridesChanged(CustomEntity entity) {
        Entity real = realEntity(entity);
        if (real != null) {
            var pdc = real.getPersistentDataContainer();
            Map<String, Object> overrides = entity.overrides();
            if (overrides.isEmpty()) pdc.remove(overridesKey);
            else pdc.set(overridesKey, PersistentDataType.STRING, GSON.toJson(overrides));
        }
        resizeHitbox(entity);
        rerender(entity);
    }

    /** La hitbox de l'interaction d'une entité réelle suit sa définition effective. */
    private void resizeHitbox(CustomEntity entity) {
        CustomEntityDef def = defOf(entity);
        if (def != null && entity.isReal() && realEntity(entity) instanceof Interaction interaction) {
            interaction.setInteractionWidth((float) def.hitboxWidth());
            interaction.setInteractionHeight((float) def.hitboxHeight());
        }
    }

    // ── IA ──────────────────────────────────────────────────────────────────

    private void discardBrain(CustomEntity entity) {
        CustomEntityBrain brain = brains.remove(entity);
        if (brain != null) brain.discard();
    }

    /**
     * Fait tourner l'IA vanilla des entités qui en ont une (paramètre {@code ai}) et leur applique le résultat. Une entité dont le
     * chunk n'est pas chargé est figée : l'IA ne charge jamais de chunk.
     */
    private void tickBrains() {
        for (CustomEntity entity : all()) {
            if (entity.isDestroyed()) continue;
            CustomEntityDef def = defOf(entity);
            String ai = def == null ? null : def.ai();
            if (ai == null) {
                discardBrain(entity);
                entity.failedAi(null);
                entity.headYaw(Float.NaN);
                continue;
            }
            CustomEntityBrain brain = brainOf(entity, def);
            if (brain == null) continue;
            Location at = locationOf(entity);
            try {
                if (brain.step(at)) applyBrain(entity, brain);
            } catch (Throwable t) {
                plugin.getLogger().warning("[CustomEntity] IA '" + ai + "' arrêtée : " + t);
                discardBrain(entity);
                entity.failedAi(ai);
            }
        }
    }

    /**
     * Le cerveau à jour de l'entité (créé ou recréé si l'IA ou le monde a changé), ou {@code null} si elle n'a pas d'IA, si son chunk
     * n'est pas chargé ou si l'IA demandée est refusée.
     */
    private CustomEntityBrain brainOf(CustomEntity entity, CustomEntityDef def) {
        String ai = def == null ? null : def.ai();
        if (ai == null || ai.equals(entity.failedAi())) return null;
        Location at = locationOf(entity);
        if (at == null || at.getWorld() == null || !at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return null;
        CustomEntityBrain brain = brains.get(entity);
        if (brain != null && (!brain.type().equals(ai) || brain.isDead() || !brain.world().equals(at.getWorld()))) {
            discardBrain(entity);
            brain = null;
        }
        if (brain == null) {
            brain = CustomEntityBrain.create(ai, at, plugin.getLogger());
            if (brain == null) {
                entity.failedAi(ai);
                return null;
            }
            brains.put(entity, brain);
        }
        return brain;
    }

    /**
     * Fait marcher l'entité jusqu'à un point avec la navigation vanilla de son mob d'IA.
     *
     * @param speed multiplicateur de vitesse du mob (1 = sa vitesse de marche)
     * @return {@code false} s'il n'y a pas d'IA, si le chunk n'est pas chargé ou si aucun chemin n'existe
     */
    public boolean walkTo(CustomEntity entity, Location to, double speed) {
        CustomEntityBrain brain = entity.isDestroyed() ? null : brainOf(entity, defOf(entity));
        if (brain == null || to.getWorld() == null || !to.getWorld().equals(brain.world())) return false;
        return brain.walkTo(to, speed);
    }

    /** Interrompt la marche demandée par {@link #walkTo}. */
    public void stopWalking(CustomEntity entity) {
        CustomEntityBrain brain = brains.get(entity);
        if (brain != null) brain.stopWalking();
    }

    /** {@code true} si le mob d'IA de l'entité suit un chemin. */
    public boolean isWalking(CustomEntity entity) {
        CustomEntityBrain brain = brains.get(entity);
        return brain != null && brain.isWalking();
    }

    private void applyBrain(CustomEntity entity, CustomEntityBrain brain) {
        Location to = brain.location();
        entity.headYaw(brain.headYaw());
        if (entity.isReal()) {
            Entity real = realEntity(entity);
            if (real != null) brain.moveReal(real);
            return; // la boucle de tick envoie le déplacement du visuel
        }
        entity.fakeLocation(to);
        if (!entity.sameAsSent(to)) {
            entity.markSent(to);
            for (Player viewer : viewers(entity)) CustomEntityPackets.send(viewer, CustomEntityPackets.move(entity, to));
        }
    }

    // ── Visibilité par joueur ───────────────────────────────────────────────

    private List<Player> viewers(CustomEntity entity) {
        List<Player> list = new ArrayList<>();
        for (UUID uuid : entity.viewers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) list.add(p);
        }
        return list;
    }

    private void show(CustomEntity entity, Player player) {
        CustomEntityDef def = defOf(entity);
        Location at = locationOf(entity);
        if (def == null || at == null || entity.isDestroyed()) return;
        if (!entity.viewers().add(player.getUniqueId())) return;
        CustomEntityPackets.send(player, CustomEntityPackets.show(entity, def, at));
        entity.markSent(at);
    }

    private void hide(CustomEntity entity, Player player) {
        if (entity.viewers().remove(player.getUniqueId())) CustomEntityPackets.send(player, CustomEntityPackets.destroy(entity));
    }

    private void hideAll(CustomEntity entity) {
        for (Player viewer : viewers(entity)) CustomEntityPackets.send(viewer, CustomEntityPackets.destroy(entity));
        entity.viewers().clear();
    }

    /** Renvoie le visuel à ceux qui le voient (la définition a changé) et l'envoie à ceux qui suivent l'entité. */
    private void rerender(CustomEntity entity) {
        List<Player> current = viewers(entity);
        hideAll(entity);
        if (entity.isFake()) {
            refreshViewers(entity);
            return;
        }
        Entity real = realEntity(entity);
        if (real == null) return;
        Set<Player> targets = new java.util.LinkedHashSet<>(current);
        targets.addAll(real.getTrackedBy());
        for (Player p : targets) show(entity, p);
    }

    /** Montre ou retire une entité fausse selon la distance de chaque joueur. */
    private void refreshViewers(CustomEntity entity) {
        Location at = entity.fakeLocation();
        if (at == null || at.getWorld() == null || entity.isDestroyed()) return;
        double show = plugin.getConfig().getDouble("custom_entity.view_distance", 48.0);
        double hide = show + HIDE_MARGIN;
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean sameWorld = p.getWorld().equals(at.getWorld());
            double distance = sameWorld ? p.getLocation().distanceSquared(at) : Double.MAX_VALUE;
            if (entity.isViewedBy(p)) {
                if (!sameWorld || distance > hide * hide) hide(entity, p);
            } else if (sameWorld && distance <= show * show) {
                show(entity, p);
            }
        }
    }

    /** Pour une entité réelle : montre aux joueurs qui la suivent, retire à ceux qui ne la suivent plus. */
    private void reconcile(CustomEntity entity, Entity real) {
        Set<Player> tracking = real.getTrackedBy();
        for (Player p : tracking) show(entity, p);
        for (Player p : viewers(entity)) {
            if (!tracking.contains(p)) hide(entity, p);
        }
    }

    // ── Boucle ──────────────────────────────────────────────────────────────

    private void tick() {
        ticks++;
        tickBrains();
        for (CustomEntity entity : reals.values()) {
            Entity real = Bukkit.getEntity(entity.realUuid());
            if (real == null || !real.isValid()) continue;
            if (ticks % REFRESH_TICKS == 0) reconcile(entity, real);
            if (entity.viewers().isEmpty()) continue;
            Location at = real.getLocation();
            if (!entity.sameAsSent(at)) {
                entity.markSent(at);
                for (Player viewer : viewers(entity)) CustomEntityPackets.send(viewer, CustomEntityPackets.move(entity, at));
            }
        }
        if (ticks % REFRESH_TICKS == 0) {
            for (CustomEntity entity : fakes) refreshViewers(entity);
        }
    }

    // ── Événements ──────────────────────────────────────────────────────────

    @EventHandler
    public void onTrack(PlayerTrackEntityEvent event) {
        Entity tracked = event.getEntity();
        if (idOf(tracked) == null) return;
        Player player = event.getPlayer();
        // le packet de création de l'interaction part après l'évènement
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            CustomEntity entity = handleOf(tracked);
            if (entity != null && player.isOnline() && tracked.isValid() && tracked.getTrackedBy().contains(player)) show(entity, player);
        }, 2L);
    }

    /** Une entité réelle qui se charge démarre son IA même sans joueur à proximité. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (idOf(entity) != null) handleOf(entity);
        }
    }

    @EventHandler
    public void onUntrack(PlayerUntrackEntityEvent event) {
        CustomEntity entity = reals.get(event.getEntity().getUniqueId());
        if (entity != null) hide(entity, event.getPlayer());
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        CustomEntity entity = reals.remove(event.getEntity().getUniqueId());
        if (entity != null) forget(entity);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forgetPlayer(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        forgetPlayer(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        forgetPlayer(event.getPlayer());
    }

    /** Le client a vidé ses entités : on oublie ce qu'on lui avait envoyé (la boucle le renverra). */
    private void forgetPlayer(Player player) {
        for (CustomEntity e : reals.values()) e.viewers().remove(player.getUniqueId());
        for (CustomEntity e : fakes) e.viewers().remove(player.getUniqueId());
    }

    // ── Clics ───────────────────────────────────────────────────────────────

    private void registerPacketListener() {
        packetListener = new PacketListenerAbstract() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
                if (!(event.getPlayer() instanceof Player player)) return;
                WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
                CustomEntity entity = byPacketId.get(wrapper.getEntityId());
                if (entity == null) return;
                // visuels et interactions fausses n'existent pas pour le serveur
                boolean virtual = entity.isFake() || wrapper.getEntityId() == entity.visualId();
                if (virtual) event.setCancelled(true);
                ClickType click = switch (wrapper.getAction()) {
                    case ATTACK -> ClickType.LEFT;
                    case INTERACT -> wrapper.getHand() == InteractionHand.MAIN_HAND ? ClickType.RIGHT : null;
                    default -> null;
                };
                if (click == null) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!entity.isDestroyed() && player.isOnline()) Bukkit.getPluginManager().callEvent(new CustomEntityInteractEvent(player, entity, click));
                });
            }
        };
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);
    }

    /** Toutes les entités actuellement connues (réelles suivies et fausses). */
    public Collection<CustomEntity> all() {
        List<CustomEntity> list = new ArrayList<>(reals.values());
        list.addAll(fakes);
        return list;
    }

    /** Monde d'une entité custom, ou {@code null}. */
    public World worldOf(CustomEntity entity) {
        Location at = locationOf(entity);
        return at == null ? null : at.getWorld();
    }
}
