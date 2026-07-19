package skwhy.pathfinder;

// ── Guava ────────────────────────────────────────────────────────────────────
import com.google.common.collect.Maps;


// ── Java std ─────────────────────────────────────────────────────────────────
import java.util.Map;
import java.util.Optional;

// ── Bukkit ───────────────────────────────────────────────────────────────────
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.util.Vector;
import org.bukkit.entity.Entity;

import skwhy.pathfinder.Navigation.PathfindingType;
// ── Internal ─────────────────────────────────────────────────────────────────
import skwhy.pathfinder.control.BodyRotationControl;
import skwhy.pathfinder.control.JumpControl;
import skwhy.pathfinder.control.LookControl;
import skwhy.pathfinder.control.MoveControl;
import skwhy.pathfinder.navigation.AmphibiousPathNavigation;
import skwhy.pathfinder.navigation.FlyingPathNavigation;
import skwhy.pathfinder.navigation.GroundPathNavigation;
import skwhy.pathfinder.navigation.PathNavigation;
import skwhy.pathfinder.navigation.WallClimberNavigation;
import skwhy.pathfinder.navigation.WaterBoundPathNavigation;
import skwhy.pathfinder.pathcalculator.PathType;

public class Mob {

    // ════════════════════════════════════════════════════════════════════════
    // ░░ SECTION 1 — Entity (champs & logique de base) ░░
    // ════════════════════════════════════════════════════════════════════════

    // ── Identité ─────────────────────────────────────────────────────
    private final Entity entity;
    private final Integer id;
    private final Navigation navigation;

    private Vector hitbox;
    private Vector position;
    private World world;

    private PathfindingType pathfindingType;
    private float speed;
    private float maxUpStep = 0.6F;

    // ── Position ─────────────────────────────────────────────────────────────

    /** Positions précédentes pour interpolation / delta. */
    public double xo, yo, zo;
    public double xOld, yOld, zOld;

    // ── Vitesse (delta movement) ─────────────────────────────────────────────
    private Vector deltaMovement = new Vector(0, 0, 0);

    // ── Rotation ─────────────────────────────────────────────────────────────
    private float yRot;
    private float xRot;
    public float yRotO;
    public float xRotO;

    // ── Flags de collision / mouvement ───────────────────────────────────────
    private boolean onGround;
    public boolean horizontalCollision;
    protected Vector stuckSpeedMultiplier = new Vector(0, 0, 0);

    // ── Chute ────────────────────────────────────────────────────────────────
    public double fallDistance;
    private boolean isNoGravity;

    // ── Compteurs de pas / distance ──────────────────────────────────────────
    public float moveDist;
    public float flyDist;

    // ── État fluide ──────────────────────────────────────────────────────────
    protected boolean wasTouchingWater;
    protected boolean wasEyeInWater;
    public boolean isInPowderSnow;
    public boolean wasInPowderSnow;

    // ── Flags partagés (bitmask) ─────────────────────────────────────────────
    private byte sharedFlags = 0;

    // ── Divers ───────────────────────────────────────────────────────────────
    protected boolean firstTick = true;
    public int tickCount;
    protected final java.util.Random random = new java.util.Random();
    public boolean needsSync;

    // ════════════════════════════════════════════════════════════════════════
    // ░░ SECTION 2 — LivingEntity (champs & logique) ░░
    // ════════════════════════════════════════════════════════════════════════

    public static final double MIN_MOVEMENT_DISTANCE = 0.003;
    public static final double DEFAULT_BASE_GRAVITY = 0.08;
    protected static final float INPUT_FRICTION = 0.98F;
    public static final float BASE_JUMP_POWER = 0.42F;
    protected static final float DEFAULT_KNOCKBACK = 0.4F;

    private boolean discardFriction = false;
    protected boolean jumping;
    public float xxa;
    public float yya;
    public float zza;
    // private int noJumpDelay;
    protected int fallFlyTicks;

    /** Position de la dernière surface grimpable utilisée. */
    private Optional<Location> lastClimbablePos = Optional.empty();

    // ════════════════════════════════════════════════════════════════════════
    // ░░ SECTION 3 — Mob (champs & logique IA) ░░
    // ════════════════════════════════════════════════════════════════════════

    protected LookControl lookControl;
    protected MoveControl moveControl;
    protected JumpControl jumpControl;
    private BodyRotationControl bodyRotationControl;
    protected PathNavigation pathnavigation;

    private final Map<PathType, Float> pathfindingMalus = Maps.newEnumMap(PathType.class);

    public float yBodyRot;
    public float yHeadRot;

    // ════════════════════════════════════════════════════════════════════════
    // ░░ CONSTRUCTEUR ░░
    // ════════════════════════════════════════════════════════════════════════

    public Mob(Navigation navigation, Entity entity, PathfindingType pathfindingType, float speed) {
        this.id = null;
        this.entity = entity;
        this.navigation = navigation;

        this.pathfindingType = pathfindingType;
        this.speed = speed;

        // Contrôles IA (Mob)
        this.lookControl = new LookControl(this);
        this.moveControl = new MoveControl(this);
        this.jumpControl = new JumpControl(this);
        this.bodyRotationControl = this.createBodyControl();
        this.pathnavigation = this.createNavigation();
    }

    public Mob(Navigation navigation, int id, Vector hitbox, Location location, PathfindingType pathfindingType,
            float speed) {
        this.id = id;
        this.entity = null;
        this.navigation = navigation;
        this.hitbox = hitbox;
        this.position = new Vector(location.getX(), location.getY(), location.getZ());
        this.world = location.getWorld();

        this.pathfindingType = pathfindingType;
        this.speed = speed;

        // Contrôles IA (Mob)
        this.lookControl = new LookControl(this);
        this.moveControl = new MoveControl(this);
        this.jumpControl = new JumpControl(this);
        this.bodyRotationControl = this.createBodyControl();
        this.pathnavigation = this.createNavigation();
    }

    // ════════════════════════════════════════════════════════════════════════
    // ░░ POSITION ░░
    // ════════════════════════════════════════════════════════════════════════

    public void setPos(final Vector pos) {
        this.setPos(pos.getX(), pos.getY(), pos.getZ());
    }

    public final void setPos(final double x, final double y, final double z) {
        if (isRealEntity())
            entity.teleport(new Location(entity.getWorld(), x, y, z));
        else {
            this.position = new Vector(x, y, z);
            navigation.sendTeleportPacket(new Location(world, x, y, z));
        }
    }

    private void moveTo(final Vector movement) {
        if (isRealEntity())
            entity.teleport(entity.getLocation().add(movement));
        else
            navigation.sendMovePacket(movement);
    }

    public Vector position() {
        return new Vector(getX(), getY(), getZ());
    }

    public double getX() {
        return (isRealEntity()) ? entity.getX() : position.getX();
    }

    public double getY() {
        return (isRealEntity()) ? entity.getY() : position.getY();
    }

    public double getZ() {
        return (isRealEntity()) ? entity.getZ() : position.getZ();
    }

    /** Retourne un {@link Location} Bukkit cohérent avec la position interne. */
    public Location getLocation() {
        return (isRealEntity()) ? entity.getLocation() : new Location(world, getX(), getY(), getZ(), yRot, xRot);
    }

    /** Retourne un {@link Location} Bukkit cohérent avec la position interne. */
    public void setLocation(Location location) {
        if (isRealEntity())
            entity.teleport(location);
        else {
            this.position = new Vector(location.getX(), location.getY(), location.getZ());
            this.world = location.getWorld();
        }
    }

    public void setYawPitch(float yaw, float pitch) {
        if (Float.isFinite(yaw) && Float.isFinite(pitch))
            if (isRealEntity())
                entity.setRotation(yaw, pitch);
            else {
                this.yRot = yaw;
                this.xRot = pitch;
            }
    }

    public void setYaw(float yaw) {
        setYawPitch(yaw, (isRealEntity()) ? entity.getPitch() : xRot);
    }

    public void setPitch(float pitch) {
        setYawPitch((isRealEntity()) ? entity.getYaw() : yRot, pitch);
    }

    /**
     * Retourne un {@link Block} Bukkit correspondant à la case occupée.
     * Remplace {@code blockPosition()} NMS.
     */
    public Block blockPosition() {
        return getWorld().getBlockAt(
                (int) Math.floor(getX()),
                (int) Math.floor(getY()),
                (int) Math.floor(getZ()));
    }

    /** Bloc à la position courante (alias lisible). */
    public Block getInBlock() {
        return blockPosition();
    }

    /** Ancienne position pour interpolation. */
    public Vector oldPosition() {
        return new Vector(xOld, yOld, zOld);
    }

    // ── Snap / téléportation ─────────────────────────────────────────────────

    public void absSnapTo(final double x, final double y, final double z) {
        double cx = Math.max(-3.0e7, Math.min(3.0e7, x));
        double cz = Math.max(-3.0e7, Math.min(3.0e7, z));
        this.xo = cx;
        this.yo = y;
        this.zo = cz;
        this.setPos(cx, y, cz);
    }

    public void absSnapTo(final double x, final double y, final double z, final float yRot, final float xRot) {
        this.absSnapTo(x, y, z);
        this.absSnapRotationTo(yRot, xRot);
    }

    public void absSnapRotationTo(final float yRot, final float xRot) {
        this.setYaw(yRot % 360.0f);
        this.setPitch((float) Math.max(-90f, Math.min(90f, xRot)) % 360.0f);
        this.yRotO = this.getYaw();
        this.xRotO = this.getPitch();
    }

    public void snapTo(final double x, final double y, final double z) {
        this.snapTo(x, y, z, this.yRot, this.xRot);
    }

    public void snapTo(final double x, final double y, final double z, final float yRot, final float xRot) {
        this.setPos(x, y, z);
        this.setYaw(yRot);
        this.setPitch(xRot);
    }

    public final void setOldPosAndRot() {
        this.setOldPos();
        this.setOldRot();
    }

    protected void setOldPos() {
        this.xo = this.xOld = getX();
        this.yo = this.yOld = getY();
        this.zo = this.zOld = getZ();
    }

    public void setOldRot() {
        this.yRotO = this.yRot;
        this.xRotO = this.xRot;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ░░ ROTATION ░░
    // ════════════════════════════════════════════════════════════════════════

    public float getYaw() {
        return (isRealEntity()) ? entity.getYaw() : yRot;
    }

    public float getPitch() {
        return (isRealEntity()) ? entity.getPitch() : xRot;
    }

    public Vector getHitbox() {
        return (isRealEntity()) ? new Vector(entity.getWidth(), entity.getHeight(), entity.getWidth())
                : this.hitbox.clone();
    }

    public void setHitbox(Vector hitbox) {
        if (isRealEntity())
            return;
        this.hitbox = hitbox.clone();
    }

    public boolean isRealEntity() {
        return !(this.entity == null);
    }

    /** Rotation progressive vers un cap cible (angle en degrés, pas max). */
    public void turn(final double xo, final double yo) {
        float xDelta = (float) yo * 0.15f;
        float yDelta = (float) xo * 0.15f;
        this.setPitch(this.xRot + xDelta);
        this.setYaw(this.yRot + yDelta);
        this.xRotO += xDelta;
        this.yRotO += yDelta;
    }

    /** Vecteur unitaire dans la direction du regard. */
    public Vector getLookAngle() {
        return calculateViewVector(this.xRot, this.yRot);
    }

    public Vector calculateViewVector(final float xRot, final float yRot) {
        float rx = (float) Math.toRadians(xRot);
        float ry = (float) Math.toRadians(-yRot);
        float yCos = (float) Math.cos(ry);
        float ySin = (float) Math.sin(ry);
        float xCos = (float) Math.cos(rx);
        float xSin = (float) Math.sin(rx);
        return new Vector(ySin * xCos, -xSin, yCos * xCos);
    }

    /** Direction cardinale la plus proche. */
    public BlockFace getDirection() {
        return Mth.getCardinalDirection(yRot);
    }

    /** Interpolation linéaire d'un angle en degrés (rotLerp). */
    // private float rotlerpDeg(final float a, final float b, final float max) {
    // float diff = (float) wrapDegrees(b - a);
    // diff = Math.max(-max, Math.min(max, diff));
    // float result = a + diff;
    // if (result < 0f)
    // result += 360f;
    // if (result > 360f)
    // result -= 360f;
    // return result;
    // }

    // ════════════════════════════════════════════════════════════════════════
    // ░░ DELTA MOVEMENT (vélocité) ░░
    // ════════════════════════════════════════════════════════════════════════

    public Vector getDeltaMovement() {
        return deltaMovement.clone();
    }

    public void setDeltaMovement(final Vector v) {
        if (Mth.isVectorFinite(v))
            this.deltaMovement = v.clone();
    }

    public void setDeltaMovement(final double xd, final double yd, final double zd) {
        this.setDeltaMovement(new Vector(xd, yd, zd));
    }

    public void addDeltaMovement(final Vector momentum) {
        if (Mth.isVectorFinite(momentum))
            this.setDeltaMovement(this.getDeltaMovement().add(momentum));
    }

    /** Impulse brute (xa, ya, za) appliquée à la vélocité. */
    public void push(final double xa, final double ya, final double za) {
        if (Double.isFinite(xa) && Double.isFinite(ya) && Double.isFinite(za)) {
            this.setDeltaMovement(this.getDeltaMovement().add(new Vector(xa, ya, za)));
            this.needsSync = true;
        }
    }

    public void push(final Vector impulse) {
        if (Mth.isVectorFinite(impulse))
            this.push(impulse.getX(), impulse.getY(), impulse.getZ());
    }

    // ════════════════════════════════════════════════════════════════════════
    // ░░ MOUVEMENT PRINCIPAL ░░
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Déplace l'entité de {@code delta}, en appliquant la physique de collision.
     * La résolution réelle des collisions est déléguée à {@link #collide(Vector)}
     * pour permettre aux sous-classes d'injecter leur propre logique.
     */
    public void move(Vector delta) {
        Vector movement = this.collide(delta);
        double movementLength = movement.lengthSquared();

        if (movementLength > 1.0e-7 || delta.lengthSquared() - movementLength < 1.0e-7) this.moveTo(movement);


        if (Math.abs(delta.getY() - movement.getY()) > 1.0e-5) {
            this.deltaMovement.setY(0);
            if (delta.getY() < 0.0) this.setOnGround(true);
        }

        if (Math.abs(delta.getX() - movement.getX()) > 1.0e-5) this.deltaMovement.setX(0);
        if (Math.abs(delta.getZ() - movement.getZ()) > 1.0e-5) this.deltaMovement.setZ(0);

        float blockSpeedFactor = this.getBlockSpeedFactor();
        Vector dm = this.getDeltaMovement();
        this.setDeltaMovement(dm.getX() * blockSpeedFactor, dm.getY(), dm.getZ() * blockSpeedFactor);
    }

    private Vector collide(final Vector movement) {
        return Collide.collide(this, movement);
    }

    // ── Entrée relative ──────────────────────────────────────────────────────

    /**
     * Ajoute un mouvement dans la direction du regard de l'entité.
     * Remplace {@code moveRelative()} NMS.
     */
    public void moveRelative(final float speed, final Vector input) {
        Vector delta = getInputVector(input, speed, this.yRot);
        this.setDeltaMovement(this.getDeltaMovement().add(delta));
    }

    protected static Vector getInputVector(final Vector input, final float speed, final float yRot) {
        double length = input.lengthSquared();
        if (length < 1.0e-7)
            return new Vector(0, 0, 0);
        Vector movement = (length > 1.0 ? input.clone().normalize() : input.clone()).multiply(speed);
        double sin = Math.sin(Math.toRadians(yRot));
        double cos = Math.cos(Math.toRadians(yRot));
        return new Vector(
                movement.getX() * cos - movement.getZ() * sin,
                movement.getY(),
                movement.getZ() * cos + movement.getX() * sin);
    }

    // ── Travel (physique de déplacement) ─────────────────────────────────────

    /**
     * Point d'entrée principal du déplacement physique chaque tick.
     * Délègue vers les modes : fluide, vol-plané, air.
     */
    public void travel(final Vector input) {
        if (this.isInWater() || this.isInLava()) {
            this.travelInFluid(input);
        } else if (this.isFallFlying()) {
            this.travelFallFlying(input);
        } else {
            this.travelInAir(input);
        }
    }

    private void travelInAir(final Vector input) {
        float blockFriction = this.onGround
                ? getBlockFriction(getBlockBelowFeet())
                : 1.0F;
        float friction = blockFriction * 0.91F;

        // 1. Appliquer la gravité sur deltaMovement AVANT move()
        if (!this.shouldDiscardFriction() && !this.isNoGravity()) {
            this.setDeltaMovement(
                getDeltaMovement().getX(),
                getDeltaMovement().getY() - this.getEffectiveGravity(),
                getDeltaMovement().getZ()
            );
        }

        // 2. moveRelative + move() via handleRelativeFriction
        Vector movement = this.handleRelativeFrictionAndCalculateMovement(input, blockFriction);

        // 3. Appliquer la friction horizontale (et verticale si créature volante)
        float vertFriction = isFlyingCreature() ? friction : 0.98F;
        if (this.shouldDiscardFriction()) {
            this.setDeltaMovement(movement.getX(), movement.getY(), movement.getZ());
        } else {
            this.setDeltaMovement(
                movement.getX() * friction,
                movement.getY() * vertFriction,
                movement.getZ() * friction
            );
        }
    }

    /** Retourne vrai si l'entité est une créature volante (stub à surcharger). */
    protected boolean isFlyingCreature() {
        return false;
    }

    private void travelInFluid(final Vector input) {
        boolean isFalling = this.deltaMovement.getY() <= 0.0;
        double oldY = this.getY();
        double baseGrav = this.getEffectiveGravity();
        if (this.isInWater()) {
            this.travelInWater(input, baseGrav, isFalling, oldY);
        } else {
            this.travelInLava(input, baseGrav, isFalling, oldY);
        }
    }

    protected void travelInWater(final Vector input, final double baseGravity,
            final boolean isFalling, final double oldY) {
        float slowDown = this.getWaterSlowDown();

        this.moveRelative(speed, input);
        this.move(this.getDeltaMovement());
        Vector ladderMovement = this.getDeltaMovement();

        if (this.horizontalCollision && this.onClimbable()) {
            ladderMovement = new Vector(ladderMovement.getX(), 0.2, ladderMovement.getZ());
        }

        ladderMovement = new Vector(
                ladderMovement.getX() * slowDown,
                ladderMovement.getY() * 0.8F,
                ladderMovement.getZ() * slowDown);
        this.setDeltaMovement(this.getFluidFallingAdjustedMovement(baseGravity, isFalling, ladderMovement));
        this.jumpOutOfFluid(oldY);
    }

    private void travelInLava(final Vector input, final double baseGravity,
            final boolean isFalling, final double oldY) {
        this.moveRelative(0.02F, input);
        this.move(this.getDeltaMovement());

        // Hauteur de fluide simplifiée : si dans la lave, on considère immergé
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.5));
        if (baseGravity != 0.0) {
            this.setDeltaMovement(this.getDeltaMovement().add(new Vector(0, -baseGravity / 4.0, 0)));
        }
        this.jumpOutOfFluid(oldY);
    }

    private void jumpOutOfFluid(final double oldY) {
        Vector movement = this.getDeltaMovement();
        if (this.horizontalCollision
                && isFree(movement.getX(), movement.getY() + 0.6F - this.getY() + oldY, movement.getZ())) {
            this.setDeltaMovement(movement.getX(), 0.3F, movement.getZ());
        }
    }

    private void travelFallFlying(final Vector input) {
        if (this.onClimbable()) {
            this.travelInAir(input);
            this.stopFallFlying();
        } else {
            Vector lastMovement = this.getDeltaMovement();
            this.setDeltaMovement(this.updateFallFlyingMovement(lastMovement));
            this.move(this.getDeltaMovement());
        }
    }

    public void stopFallFlying() {
        /* stub — gérer l'état FallFlying en sous-classe */ }

    private Vector updateFallFlyingMovement(Vector movement) {
        Vector lookAngle = this.getLookAngle();
        float leanAngle = (float) Math.toRadians(this.xRot);
        double lookHorLen = Math.sqrt(lookAngle.getX() * lookAngle.getX() + lookAngle.getZ() * lookAngle.getZ());
        double moveHorLen = Math.sqrt(movement.getX() * movement.getX() + movement.getZ() * movement.getZ());
        double gravity = this.getEffectiveGravity();
        double liftForce = Math.pow(Math.cos(leanAngle), 2);

        movement = movement.clone().add(new Vector(0, gravity * (-1.0 + liftForce * 0.75), 0));

        if (movement.getY() < 0.0 && lookHorLen > 0.0) {
            double convert = movement.getY() * -0.1 * liftForce;
            movement = movement.add(new Vector(
                    lookAngle.getX() * convert / lookHorLen,
                    convert,
                    lookAngle.getZ() * convert / lookHorLen));
        }

        if (leanAngle < 0.0F && lookHorLen > 0.0) {
            double convert = moveHorLen * -Math.sin(leanAngle) * 0.04;
            movement = movement.add(new Vector(
                    -lookAngle.getX() * convert / lookHorLen,
                    convert * 3.2,
                    -lookAngle.getZ() * convert / lookHorLen));
        }

        if (lookHorLen > 0.0) {
            movement = movement.add(new Vector(
                    (lookAngle.getX() / lookHorLen * moveHorLen - movement.getX()) * 0.1,
                    0,
                    (lookAngle.getZ() / lookHorLen * moveHorLen - movement.getZ()) * 0.1));
        }

        return movement.multiply(new Vector(0.99F, 0.98F, 0.99F));
    }

    private Vector handleRelativeFrictionAndCalculateMovement(final Vector input, final float friction) {
        this.moveRelative(this.getFrictionInfluencedSpeed(friction), input);
        this.setDeltaMovement(this.handleOnClimbable(this.getDeltaMovement()));
        this.move(this.getDeltaMovement());
        Vector movement = this.getDeltaMovement();

        if ((this.horizontalCollision || this.jumping)
                && (this.onClimbable() || (this.wasInPowderSnow && canWalkOnPowderSnow()))) {
            movement = new Vector(movement.getX(), 0.2, movement.getZ());
        }

        return movement;
    }

    private Vector handleOnClimbable(Vector delta) {
        if (this.onClimbable()) {
            double xd = Math.max(-0.15F, Math.min(0.15F, delta.getX()));
            double zd = Math.max(-0.15F, Math.min(0.15F, delta.getZ()));
            double yd = Math.max(delta.getY(), -0.15F);
            // Suppression de la glissade sur échelle si shift (joueur uniquement)
            delta = new Vector(xd, yd, zd);
        }
        return delta;
    }

    public Vector getFluidFallingAdjustedMovement(final double baseGravity,
            final boolean isFalling,
            final Vector movement) {
        if (baseGravity != 0.0) {
            double yd;
            if (isFalling
                    && Math.abs(movement.getY() - 0.005) >= 0.003
                    && Math.abs(movement.getY() - baseGravity / 16.0) < 0.003) {
                yd = -0.003;
            } else {
                yd = movement.getY() - baseGravity / 16.0;
            }
            return new Vector(movement.getX(), yd, movement.getZ());
        }
        return movement;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ░░ SOL / SUPPORT ░░
    // ════════════════════════════════════════════════════════════════════════

    public void setOnGround(final boolean onGround) {
        this.onGround = onGround;
    }

    public void setOnGroundWithMovement(final boolean onGround,
            final boolean horizontalCollision) {
        this.onGround = onGround;
        this.horizontalCollision = horizontalCollision;
    }

    public boolean onGround() {
        return this.onGround;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ░░ HITBOX ░░
    // ════════════════════════════════════════════════════════════════════════

    public double getMinX() {
        return getX() - getHitbox().getX() / 2.0;
    }

    public double getMinY() {
        return getY();
    }

    public double getMinZ() {
        return getZ() - getHitbox().getZ() / 2.0;
    }

    public double getMaxX() {
        return getX() + getHitbox().getX() / 2.0;
    }

    public double getMaxY() {
        return getY() + getHitbox().getY();
    }

    public double getMaxZ() {
        return getZ() + getHitbox().getZ() / 2.0;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ░░ GRAVITÉ & CHUTE ░░
    // ════════════════════════════════════════════════════════════════════════

    public double getGravity() {
        return this.isNoGravity() ? 0.0 : 0.08;
    }

    protected double getEffectiveGravity() {
        return this.getGravity();
    }

    // ════════════════════════════════════════════════════════════════════════
    // ░░ SAUT ░░
    // ════════════════════════════════════════════════════════════════════════

    public void jumpFromGround() {
        float jumpPower = 0.42F;
        if (jumpPower <= 1.0e-5F)
            return;
        Vector movement = this.getDeltaMovement();
        this.setDeltaMovement(movement.getX(), Math.max(jumpPower, movement.getY()), movement.getZ());
        this.needsSync = true;
    }

    protected void jumpInLiquid() {
        if (this.pathnavigation.canFloat()) {
            this.setDeltaMovement(this.getDeltaMovement().add(new Vector(0, 0.04, 0)));
        } else {
            this.setDeltaMovement(this.getDeltaMovement().add(new Vector(0, 0.3, 0)));
        }
    }

    protected void goDownInWater() {
        this.setDeltaMovement(this.getDeltaMovement().add(new Vector(0, -0.04F, 0)));
    }

    public boolean isJumping() {
        return this.jumping;
    }

    public void setJumping(boolean jump) {
        this.jumping = jump;
    }

    public float getMaxUpStep() {
        return maxUpStep;
    }

    public void setMaxUpStep(float maxUpStep) {
        this.maxUpStep = maxUpStep;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ░░ GRIMPABLE (CLIMBABLE) ░░
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Vérifie si l'entité est sur une surface grimpable (échelle, lierre, etc.).
     * Remplace {@code onClimbable()} NMS (qui utilisait des BlockTags).
     */
    public boolean onClimbable() {
        Block block = this.blockPosition();
        if (isClimbableMaterial(block.getType())) {
            this.lastClimbablePos = Optional.of(block.getLocation());
            return true;
        }
        // Trapdoor utilisable comme échelle
        if (block.getBlockData() instanceof TrapDoor td && td.isOpen()) {
            Block below = getWorld().getBlockAt(block.getX(), block.getY() - 1, block.getZ());
            if (below.getType() == Material.LADDER
                    && below.getBlockData() instanceof org.bukkit.block.data.type.Ladder ladder
                    && ladder.getFacing() == td.getFacing()) {
                this.lastClimbablePos = Optional.of(block.getLocation());
                return true;
            }
        }
        return false;
    }

    /**
     * Matériaux considérés comme grimpables (équivalent de BlockTags.CLIMBABLE).
     */
    private static boolean isClimbableMaterial(final Material m) {
        return switch (m) {
            case LADDER, VINE, TWISTING_VINES, WEEPING_VINES,
                    TWISTING_VINES_PLANT, WEEPING_VINES_PLANT,
                    CAVE_VINES, CAVE_VINES_PLANT, SCAFFOLDING ->
                true;
            default -> false;
        };
    }

    public Optional<Location> getLastClimbablePos() {
        return this.lastClimbablePos;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ░░ FLUIDES ░░
    // ════════════════════════════════════════════════════════════════════════

    public boolean isInWater() {
        return this.wasTouchingWater;
    }

    public boolean isInLava() {
        return false;
        /* surcharger si nécessaire */ }

    public boolean isInLiquid() {
        return this.isInWater() || this.isInLava();
    }

    public boolean isUnderWater() {
        return this.wasEyeInWater && this.isInWater();
    }

    public boolean isInShallowWater() {
        return this.isInWater() && !this.isUnderWater();
    }

    public boolean isPushedByFluid() {
        return true;
    }

    public boolean isAffectedByFluids() {
        return true;
    }

    public void updateSwimming() {
        if (this.isSwimming()) {
            this.setSwimming(this.isInWater());
        } else {
            this.setSwimming(this.isUnderWater());
        }
    }

    protected float getWaterSlowDown() {
        return 0.8F;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ░░ VITESSE & FRICTION ░░
    // ════════════════════════════════════════════════════════════════════════

    public float getSpeed() {
        return this.speed;
    }

    public void setSpeed(final float speed) {
        this.speed = speed;
        this.setZza(speed);
    }

    protected float getBlockSpeedFactor() {
        // MOVEMENT_EFFICIENCY Bukkit attribute non-standard ; valeur par défaut 1
        return 1.0f;
    }

    private float getFrictionInfluencedSpeed(final float blockFriction) {
        return this.onGround
                ? this.speed * (0.21600002F / (blockFriction * blockFriction * blockFriction))
                : this.getFlyingSpeed();
    }

    protected float getFlyingSpeed() {
        return 0.02F;
    }

    protected void applyInput() {
        this.xxa *= 0.98F;
        this.zza *= 0.98F;
    }

    /** Retourne la friction d'un bloc via son type (approximation). */
    private static float getBlockFriction(final Block block) {
        if (block == null)
            return 0.6f;
        return switch (block.getType()) {
            case ICE, PACKED_ICE -> 0.98f;
            case BLUE_ICE -> 0.989f;
            case SLIME_BLOCK -> 0.8f;
            default -> 0.6f;
        };
    }

    private Block getBlockBelowFeet() {
        return getWorld().getBlockAt(
                (int) Math.floor(getX()),
                (int) Math.floor(getY() - 0.5001),
                (int) Math.floor(getZ()));
    }

    /** Vérifie si une case est libre (pas de bloc solide). */
    protected boolean isFree(final double dx, final double dy, final double dz) {
        Block b = getWorld().getBlockAt(
                (int) Math.floor(getX() + dx),
                (int) Math.floor(getY() + dy),
                (int) Math.floor(getZ() + dz));
        return b.getType().isAir() || !b.getType().isSolid();
    }

    /**
     * Stub pour poudre de neige (requiert Paper / NMS pour vraie implémentation).
     */
    protected boolean canWalkOnPowderSnow() {
        return false;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ░░ FLAGS PARTAGÉS (bitmask) ░░
    // ════════════════════════════════════════════════════════════════════════

    protected boolean getSharedFlag(final int flag) {
        return (sharedFlags & (1 << flag)) != 0;
    }

    protected void setSharedFlag(final int flag, final boolean value) {
        if (value)
            sharedFlags |= (byte) (1 << flag);
        else
            sharedFlags &= (byte) ~(1 << flag);
    }

    public boolean isSwimming() {
        return getSharedFlag(4);
    }

    public void setSwimming(boolean v) {
        setSharedFlag(4, v);
    }

    public boolean isNoGravity() {
        return isNoGravity;
    }

    public void setNoGravity(boolean isNoGravity) {
        this.isNoGravity = isNoGravity;
    }

    public boolean isFallFlying() {
        return false;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ░░ ÉTAT DIVERS ░░
    // ════════════════════════════════════════════════════════════════════════

    public boolean isAlive() {
        return (isRealEntity()) ? entity.isValid() : true;
    }

    public boolean isRemoved() {
        return false;
    }

    public World getWorld() {
        return (isRealEntity()) ? entity.getWorld() : world;
    }

    public int getId() {
        return (isRealEntity()) ? entity.getEntityId() : id;
    }

    public Entity getEntity() {
        return (isRealEntity()) ? entity : null;
    }

    public PathfindingType getPathfindingType() {
        return pathfindingType;
    }

    public void setPathfindingType(PathfindingType pathfindingType) {
        this.pathfindingType = pathfindingType;
    }

    /** Interpolation lissée position + rotation. */
    protected void lerpPositionAndRotationStep(final int steps,
            final double tx, final double ty, final double tz,
            final double tyRot, final double txRot) {
        double alpha = 1.0 / steps;
        this.setPos(lerp(alpha, getX(), tx), lerp(alpha, getY(), ty), lerp(alpha, getZ(), tz));
        this.setYawPitch((float) rotLerp(alpha, getYaw(), tyRot), (float) lerp(alpha, getPitch(), txRot));
    }

    public boolean shouldDiscardFriction() {
        return this.discardFriction;
    }

    public void setDiscardFriction(final boolean discardFriction) {
        this.discardFriction = discardFriction;
    }

    public void tick() {
        serverAiStep();
        
        this.travel(getDeltaMovement());
    }

    // ── Bulle (bubble column) ─────────────────────────────────────────────────

    public void onAboveBubbleColumn(final boolean dragDown) {
        Vector m = this.getDeltaMovement();
        double yd = dragDown ? Math.max(-0.9, m.getY() - 0.03) : Math.min(1.8, m.getY() + 0.1);
        this.setDeltaMovement(m.getX(), yd, m.getZ());
    }

    public void onInsideBubbleColumn(final boolean dragDown) {
        Vector m = this.getDeltaMovement();
        double yd = dragDown ? Math.max(-0.3, m.getY() - 0.03) : Math.min(0.7, m.getY() + 0.06);
        this.setDeltaMovement(m.getX(), yd, m.getZ());
    }

    // ════════════════════════════════════════════════════════════════════════
    // ░░ INPUT (axes de déplacement) ░░
    // ════════════════════════════════════════════════════════════════════════

    public void setZza(final float zza) {
        this.zza = zza;
    }

    public void setYya(final float yya) {
        this.yya = yya;
    }

    public void setXxa(final float xxa) {
        this.xxa = xxa;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ░░ CONTRÔLES IA (Mob) ░░
    // ════════════════════════════════════════════════════════════════════════

    protected PathNavigation createNavigation() {
        return switch (pathfindingType) {
            case WALK -> new GroundPathNavigation(this);
            case WALK_WATER -> new AmphibiousPathNavigation(this);
            case SWIM -> new WaterBoundPathNavigation(this);
            case FLY, FLY_GROUND -> new FlyingPathNavigation(this);
            case CLIMB -> new WallClimberNavigation(this);
            case NONE -> null;
        };
    }

    public float getPathfindingMalus(final PathType pathType) {
        Float malus = this.pathfindingMalus.get(pathType);
        return malus == null ? pathType.getMalus() : malus;
    }

    public void setPathfindingMalus(final PathType pathType, final float cost) {
        this.pathfindingMalus.put(pathType, cost);
    }

    public void onPathfindingDone() {
    }

    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this);
    }

    public LookControl getLookControl() {
        return this.lookControl;
    }

    public JumpControl getJumpControl() {
        return this.jumpControl;
    }

    public MoveControl getMoveControl() {
        return this.moveControl;
    }

    public PathNavigation getNavigation() {
        return this.pathnavigation;
    }

    public void stopInPlace() {
        this.getNavigation().stop();
        this.setXxa(0.0F);
        this.setYya(0.0F);
        this.setSpeed(0.0F);
        this.setDeltaMovement(0.0, 0.0, 0.0);
    }

    // ── Rotation de tête / corps ─────────────────────────────────────────────

    protected void tickHeadTurn(final float yBodyRotT) {
        this.bodyRotationControl.clientTick();
        this.yRot = this.yBodyRot;
    }

    public int getMaxHeadXRot() {
        return 40;
    }

    public int getMaxHeadYRot() {
        return 75;
    }

    public int getHeadRotSpeed() {
        return 10;
    }

    protected void clampHeadRotationToBody() {
        float limit = this.getMaxHeadYRot();
        float headYRot = this.getYHeadRot();
        float delta = (float) wrapDegrees(this.yBodyRot - headYRot);
        float clamped = (float) Math.max(-limit, Math.min(limit, delta));
        this.setYHeadRot(headYRot + delta - clamped);
    }

    public float getYHeadRot() {
        return this.yHeadRot;
    }

    public void setYHeadRot(final float v) {
        this.yHeadRot = v;
    }

    // ── Tick IA serveur ───────────────────────────────────────────────────────

    protected final void serverAiStep() {
        this.pathnavigation.tick();
        this.moveControl.tick();
        this.lookControl.tick();
        this.jumpControl.tick();
        this.tickHeadTurn(this.yBodyRot);
    }

    // ════════════════════════════════════════════════════════════════════════
    // ░░ UTILITAIRES MATHÉMATIQUES (remplaçant Mth NMS) ░░
    // ════════════════════════════════════════════════════════════════════════

    protected static double lerp(double alpha, double a, double b) {
        return a + alpha * (b - a);
    }

    protected static double rotLerp(double alpha, double a, double b) {
        return a + alpha * wrapDegrees(b - a);
    }

    protected static double wrapDegrees(double d) {
        d %= 360.0;
        if (d >= 180.0)
            d -= 360.0;
        if (d < -180.0)
            d += 360.0;
        return d;
    }

    // ════════════════════════════════════════════════════════════════════════
    // ░░ DEBUG / AFFICHAGE ░░
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("╔════════════════════════════════════════════\n");
        sb.append("║ MOB INFO (").append(isRealEntity() ? "Vraie Entité Bukkit" : "Entité Virtuelle").append(")\n");
        sb.append("╠════════════════════════════════════════════\n");

        // --- Identité ---
        sb.append("║ [Identité]\n");
        sb.append("║ ID: ").append(getId()).append("\n");
        sb.append("║ En vie: ").append(isAlive() ? "Oui" : "Non").append("\n");
        if (getWorld() != null) {
            sb.append("║ Monde: ").append(getWorld().getName()).append("\n");
        }

        // --- Position & Rotation ---
        sb.append("║\n║ [Position & Rotation]\n");
        sb.append(String.format("║ Position: X:%.2f | Y:%.2f | Z:%.2f\n", getX(), getY(), getZ()));
        sb.append(String.format("║ Rotation: Yaw:%.2f | Pitch:%.2f\n", getYaw(), getPitch()));
        sb.append(String.format("║ Tête (Yaw): %.2f\n", getYHeadRot()));
        sb.append("║ Direction: ").append(getDirection()).append("\n");
        Block b = blockPosition();
        if (b != null) {
            sb.append("║ Bloc actuel: ").append(b.getType()).append("\n");
        }

        // --- Physique & Mouvement ---
        sb.append("║\n║ [Physique & Mouvement]\n");
        Vector dm = getDeltaMovement();
        sb.append(String.format("║ DeltaMovement: %.3f, %.3f, %.3f\n", dm.getX(), dm.getY(), dm.getZ()));
        sb.append(String.format("║ Vitesse de base: %.3f\n", getSpeed()));
        Vector hb = getHitbox();
        if (hb != null) {
            sb.append(String.format("║ Hitbox (LxHxP): %.2f x %.2f x %.2f\n", hb.getX(), hb.getY(), hb.getZ()));
        }
        sb.append("║ Distance de chute: ").append(String.format("%.2f", fallDistance)).append("\n");
        sb.append("║ Gravité désactivée: ").append(isNoGravity() ? "Oui" : "Non").append("\n");

        // --- États & Environnement ---
        sb.append("║\n║ [États]\n");
        sb.append("║ Au sol (onGround): ").append(onGround() ? "Oui" : "Non").append("\n");
        sb.append("║ En saut: ").append(isJumping() ? "Oui" : "Non").append("\n");
        sb.append("║ Dans un liquide: ").append(isInLiquid() ? "Oui" : "Non");
        if (isInLiquid()) {
            sb.append(" (Eau: ").append(isInWater()).append(", Lave: ").append(isInLava()).append(")");
        }
        sb.append("\n");
        sb.append("║ Sous l'eau: ").append(isUnderWater() ? "Oui" : "Non").append("\n");
        sb.append("║ En nage: ").append(isSwimming() ? "Oui" : "Non").append("\n");
        sb.append("║ Sur surface grimpable: ").append(onClimbable() ? "Oui" : "Non").append("\n");

        // --- IA & Navigation ---
        sb.append("║\n║ [IA & Pathfinding]\n");
        sb.append("║ Type Pathfinding: ").append(getPathfindingType() != null ? getPathfindingType().name() : "NONE")
                .append("\n");

        sb.append("╚════════════════════════════════════════════");

        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════════════
    // ░░ DEBUG MOUVEMENT ░░
    // ════════════════════════════════════════════════════════════════════════

    public String showMovement() {
        StringBuilder sb = new StringBuilder();

        sb.append("┌────────────────────────────────────────────\n");
        sb.append("│ DEBUG MOUVEMENT & PHYSIQUE \n");
        sb.append("├────────────────────────────────────────────\n");

        // --- 1. Positions et Historique ---
        sb.append("│ [Positions]\n");
        sb.append(String.format("│ Actuelle  : X:%.3f | Y:%.3f | Z:%.3f\n", getX(), getY(), getZ()));
        sb.append(String.format("│ xo,yo,zo  : X:%.3f | Y:%.3f | Z:%.3f\n", xo, yo, zo));
        sb.append(String.format("│ x,y,z Old : X:%.3f | Y:%.3f | Z:%.3f\n", xOld, yOld, zOld));

        // --- 2. Vélocités (Le moteur réel) ---
        sb.append("│\n│ [Vélocités]\n");
        Vector dm = getDeltaMovement();
        sb.append(String.format("│ deltaMovement  : %.4f, %.4f, %.4f\n", dm.getX(), dm.getY(), dm.getZ()));
        sb.append(String.format("│ speed (Base)   : %.3f\n", speed));

        // --- 3. Inputs (Ce que l'IA essaie de faire) ---
        sb.append("│\n│ [Inputs IA (Frictions & Strafe)]\n");
        sb.append(String.format("│ xxa, yya, zza : %.3f, %.3f, %.3f\n", xxa, yya, zza));
        sb.append("│ En saut (jumping) : ").append(jumping ? "Oui" : "Non").append("\n");
        sb.append("│ Ignorer friction  : ").append(discardFriction ? "Oui" : "Non").append("\n");

        // --- 4. Rotations ---
        sb.append("│\n│ [Rotations]\n");
        sb.append(String.format("│ Actuelle : yRot:%.2f | xRot:%.2f\n", yRot, xRot));
        sb.append(String.format("│ Ancienne : yRotO:%.2f | xRotO:%.2f\n", yRotO, xRotO));
        sb.append(String.format("│ Corps/Tête: yBody:%.2f | yHead:%.2f\n", yBodyRot, yHeadRot));

        // --- 5. Collisions & Gravité ---
        sb.append("│\n│ [Collisions & Gravité]\n");
        sb.append("│ onGround               : ").append(onGround).append("\n");
        sb.append("│ Sans Gravité           : ").append(isNoGravity).append("\n");
        sb.append(String.format("│ Distance chute (fall)  : %.3f\n", fallDistance));

        sb.append("└────────────────────────────────────────────");

        return sb.toString();
    }
}