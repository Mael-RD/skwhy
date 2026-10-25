package skwhy.customentity;

import java.util.Optional;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * « Cerveau » d'une entité custom : un vrai mob du serveur (zombie, villageois...) créé mais jamais ajouté au monde, donc invisible
 * et absent des listes d'entités. On le fait tourner à la main chaque tick ({@link Mob#tick()}) : ses goals, sa navigation, sa
 * physique et ses attaques sont le code vanilla. L'entité custom prend ensuite sa position et sa rotation.
 * <p>
 * Seule classe du plugin qui touche à NMS.
 */
final class CustomEntityBrain {

    private final Mob mob;
    private final String type;
    private final org.bukkit.World world;
    // Dernière position transmise à l'entité custom : si elle en diffère, quelqu'un l'a déplacée et le mob la suit
    private double lastX, lastY, lastZ;
    private float lastYaw, lastPitch;

    private CustomEntityBrain(Mob mob, String type, org.bukkit.World world) {
        this.mob = mob;
        this.type = type;
        this.world = world;
    }

    /**
     * Crée le mob de ce type à cet endroit.
     *
     * @return le cerveau, ou {@code null} (avec un avertissement) si le type n'existe pas ou n'est pas un mob
     */
    static CustomEntityBrain create(String type, Location at, Logger logger) {
        if (at.getWorld() == null) return null;
        Identifier id = Identifier.tryParse("minecraft:" + type);
        Optional<? extends EntityType<?>> entityType = id == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (entityType.isEmpty()) {
            logger.warning("[CustomEntity] IA '" + type + "' : type d'entité inconnu");
            return null;
        }
        try {
            ServerLevel level = ((CraftWorld) at.getWorld()).getHandle();
            if (!(entityType.get().create(level, EntitySpawnReason.COMMAND) instanceof Mob mob)) {
                logger.warning("[CustomEntity] IA '" + type + "' : ce n'est pas un mob (ou il ne peut pas être créé)");
                return null;
            }
            mob.setSilent(true);
            mob.setInvulnerable(true);
            mob.setPersistenceRequired();
            CustomEntityBrain brain = new CustomEntityBrain(mob, type, at.getWorld());
            brain.place(at);
            return brain;
        } catch (Throwable t) {
            logger.warning("[CustomEntity] IA '" + type + "' : création impossible (" + t + ")");
            return null;
        }
    }

    String type() { return type; }

    org.bukkit.World world() { return world; }

    boolean isDead() { return mob.isRemoved() || mob.isDeadOrDying(); }

    /** Place le mob (téléportation de l'entité custom) et retient cette position comme la dernière transmise. */
    private void place(Location at) {
        mob.snapTo(at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch());
        mob.setYHeadRot(at.getYaw());
        mob.setYBodyRot(at.getYaw());
        mob.getNavigation().stop();
        // un mob neuf n'est « au sol » qu'après un tick, et la navigation au sol refuse de chercher un chemin sinon
        mob.setOnGround(!mob.level().noCollision(mob, mob.getBoundingBox().move(0.0, -0.05, 0.0)));
        lastX = at.getX();
        lastY = at.getY();
        lastZ = at.getZ();
        lastYaw = at.getYaw();
        lastPitch = at.getPitch();
    }

    /**
     * Fait avancer le mob d'un tick à partir de la position actuelle de l'entité custom.
     *
     * @return {@code true} si le mob a bougé ou tourné (voir {@link #x()}...)
     */
    boolean step(Location current) {
        if (Math.abs(current.getX() - lastX) > 1.0E-4 || Math.abs(current.getY() - lastY) > 1.0E-4 || Math.abs(current.getZ() - lastZ) > 1.0E-4
                || Math.abs(current.getYaw() - lastYaw) > 0.01F || Math.abs(current.getPitch() - lastPitch) > 0.01F) {
            place(current);
        }
        mob.tick();
        boolean moved = mob.getX() != lastX || mob.getY() != lastY || mob.getZ() != lastZ
            || mob.getYRot() != lastYaw || mob.getXRot() != lastPitch;
        if (moved) {
            lastX = mob.getX();
            lastY = mob.getY();
            lastZ = mob.getZ();
            lastYaw = mob.getYRot();
            lastPitch = mob.getXRot();
        }
        return moved;
    }

    /** Nouvelle position du mob (après {@link #step}). */
    Location location() {
        return new Location(world, lastX, lastY, lastZ, lastYaw, lastPitch);
    }

    float headYaw() { return mob.getYHeadRot(); }

    /** Demande à la navigation vanilla un chemin jusqu'à ce point ; {@code false} s'il n'y en a pas. */
    boolean walkTo(Location to, double speed) {
        return mob.getNavigation().moveTo(to.getX(), to.getY(), to.getZ(), speed);
    }

    void stopWalking() { mob.getNavigation().stop(); }

    boolean isWalking() { return !mob.getNavigation().isDone(); }

    /** Déplace une entité réelle du serveur (l'interaction) à la position du mob, sans événement ni téléportation complète. */
    void moveReal(org.bukkit.entity.Entity real) {
        ((CraftEntity) real).getHandle().snapTo(lastX, lastY, lastZ, lastYaw, lastPitch);
    }

    void discard() {
        mob.getNavigation().stop();
    }
}
