package skwhy.pathfinder.control;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.bukkit.util.VoxelShape;
import org.bukkit.util.BoundingBox;
import org.bukkit.Tag;

import skwhy.pathfinder.Mob;

public class MoveControl implements Control {

    public static final float MIN_SPEED = 5.0E-4F;
    public static final float MIN_SPEED_SQR = 2.5000003E-7F;
    protected static final int MAX_TURN = 90;

    protected final Mob mob;

    protected double wantedX;
    protected double wantedY;
    protected double wantedZ;
    protected float strafeForwards;
    protected float strafeRight;

    protected Operation operation = Operation.WAIT;

    public MoveControl(final Mob mob) {
        this.mob = mob;
    }

    // ─── API publique ────────────────────────────────────────────────────────

    public boolean hasWanted() {
        return this.operation == Operation.MOVE_TO;
    }

    public void setWantedPosition(final double x, final double y, final double z) {
        this.wantedX = x;
        this.wantedY = y;
        this.wantedZ = z;
        if (this.operation != Operation.JUMPING) {
            this.operation = Operation.MOVE_TO;
        }
    }

    public void strafe(final float forwards, final float right) {
        this.operation = Operation.STRAFE;
        this.strafeForwards = forwards;
        this.strafeRight = right;
    }

    public void setWait() {
        this.operation = Operation.WAIT;
    }

    public double getWantedX() {
        return this.wantedX;
    }

    public double getWantedY() {
        return this.wantedY;
    }

    public double getWantedZ() {
        return this.wantedZ;
    }

    // ─── Tick principal ──────────────────────────────────────────────────────

    public void tick() {
        switch (this.operation) {
            case STRAFE -> tickStrafe();
            case MOVE_TO -> tickMoveTo();
            case JUMPING -> tickJumping();
            default -> mob.setDeltaMovement(mob.getDeltaMovement().setX(0).setZ(0));
        }
    }

    // ─── Logique STRAFE ──────────────────────────────────────────────────────

    private void tickStrafe() {
        float xa = this.strafeForwards;
        float za = this.strafeRight;
        float dist = (float) Math.sqrt(xa * xa + za * za);
        if (dist < 1.0F)
            dist = 1.0F;

        dist = mob.getSpeed() / dist;
        xa *= dist;
        za *= dist;

        float yawRad = (float) Math.toRadians(mob.getLocation().getYaw());
        float sin = (float) Math.sin(yawRad);
        float cos = (float) Math.cos(yawRad);

        float dx = xa * cos - za * sin;
        float dz = za * cos + xa * sin;

        if (!isWalkable(dx, dz)) {
            this.strafeForwards = 1.0F;
            this.strafeRight = 0.0F;
        }

        // Applique la vélocité horizontale (conserve la composante Y existante)
        Vector vel = mob.getDeltaMovement();
        mob.setDeltaMovement(new Vector(dx, vel.getY(), dz));

        this.operation = Operation.WAIT;
    }

    // ─── Logique MOVE_TO ─────────────────────────────────────────────────────

    private void tickMoveTo() {
        this.operation = Operation.WAIT;

        Location loc = mob.getLocation();
        double xd = this.wantedX - loc.getX();
        double yd = this.wantedY - loc.getY();
        double zd = this.wantedZ - loc.getZ();
        double dd = xd * xd + yd * yd + zd * zd;

        if (dd < MIN_SPEED_SQR) {
            // Destination atteinte : arrêt horizontal
            Vector vel = mob.getDeltaMovement();
            mob.setDeltaMovement(new Vector(0, vel.getY(), 0));
            return;
        }

        // Rotation vers la cible
        mob.setYaw(rotlerp(loc.getYaw(), (float) (Math.toDegrees(Math.atan2(zd, xd))) - 90.0F, MAX_TURN)); // met à jour
                                                                                                           // le yaw
                                                                                                           // serveur

        // Vélocité horizontale vers la cible
        double horiz = Math.sqrt(xd * xd + zd * zd);
        double vx = (xd / horiz) * mob.getSpeed();
        double vz = (zd / horiz) * mob.getSpeed();

        Vector currentVel = mob.getDeltaMovement();

        // Saut si nécessaire
        float bbWidth = (float) mob.getHitbox().getX();
        Block standingBlock = loc.getBlock();
        Material mat = standingBlock.getType();
        VoxelShape shape = standingBlock.getCollisionShape();
        boolean hasCollision = !shape.getBoundingBoxes().isEmpty();
        double maxShapeY = 0.0;
        if (hasCollision) {
            for (BoundingBox box : shape.getBoundingBoxes()) {
                if (box.getMaxY() > maxShapeY) {
                    maxShapeY = box.getMaxY();
                }
            }
        }

        if ((yd > getMaxUpStep() && xd * xd + zd * zd < Math.max(1.0F, bbWidth))
                || (hasCollision
                        && loc.getY() < standingBlock.getY() + maxShapeY
                        && !(Tag.DOORS.isTagged(mat)
                                || Tag.FENCES.isTagged(mat)
                                || Tag.WOODEN_FENCES.isTagged(mat)))) {
            mob.setDeltaMovement(new Vector(vx, 0.42, vz)); // 0.42 = force de saut vanilla
            this.operation = Operation.JUMPING;
        } else {
            mob.setDeltaMovement(new Vector(vx, currentVel.getY(), vz));
        }
    }

    // ─── Logique JUMPING ─────────────────────────────────────────────────────

    private void tickJumping() {
        Location loc = mob.getLocation();
        double xd = this.wantedX - loc.getX();
        double zd = this.wantedZ - loc.getZ();
        double horiz = Math.sqrt(xd * xd + zd * zd);

        if (horiz > 1e-5) {
            double vx = (xd / horiz) * mob.getSpeed();
            double vz = (zd / horiz) * mob.getSpeed();
            Vector vel = mob.getDeltaMovement();
            mob.setDeltaMovement(new Vector(vx, vel.getY(), vz));
        }

        // Fin du saut : au sol ou dans un liquide avec flottaison
        if (mob.onGround() || isInLiquidAndAffected()) {
            this.operation = Operation.WAIT;
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Hauteur max qu'une entité peut franchir sans sauter.
     * Bukkit n'expose pas maxUpStep, on utilise 0.6 (valeur vanilla standard).
     */
    private double getMaxUpStep() {
        return 0.6;
    }

    /** Vérifie si la case cible est praticable (non-liquide, non-danger). */
    private boolean isWalkable(final float dx, final float dz) {
        Location loc = mob.getLocation();
        Block target = loc.getWorld().getBlockAt(
                (int) Math.floor(loc.getX() + dx),
                loc.getBlockY(),
                (int) Math.floor(loc.getZ() + dz));

        Material type = target.getType();
        return !type.isEmpty()
                || (!isLiquid(type) && !isDangerous(type));
    }

    /** Indique si l'entité est dans un liquide et affectée par celui-ci. */
    private boolean isInLiquidAndAffected() {
        return mob.isInWater() || mob.isInLava();
    }

    private static boolean isLiquid(Material m) {
        return m == Material.WATER || m == Material.LAVA;
    }

    private static boolean isDangerous(Material m) {
        return m == Material.FIRE
                || m == Material.LAVA
                || m.name().contains("CACTUS");
    }

    private static boolean isDoorOrFence(Material m) {
        String name = m.name();
        return name.contains("DOOR") || name.contains("FENCE") || name.contains("GATE");
    }

    /**
     * Interpole un angle {@code a} vers {@code b} avec un pas maximum de
     * {@code max}
     * degrés, en restant dans [0, 360[.
     */
    protected float rotlerp(final float a, final float b, final float max) {
        float diff = wrapDegrees(b - a);
        diff = Math.max(-max, Math.min(max, diff));
        float result = a + diff;
        if (result < 0.0F)
            result += 360.0F;
        if (result > 360.0F)
            result -= 360.0F;
        return result;
    }

    private static float wrapDegrees(float deg) {
        deg %= 360.0F;
        if (deg >= 180.0F)
            deg -= 360.0F;
        if (deg < -180.0F)
            deg += 360.0F;
        return deg;
    }

    // ─── Enum ────────────────────────────────────────────────────────────────

    protected enum Operation {
        WAIT, MOVE_TO, STRAFE, JUMPING
    }
}