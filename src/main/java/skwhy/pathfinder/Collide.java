package skwhy.pathfinder;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.util.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Adapted from net.minecraft.world.entity.Entity#collide and its private
 * helpers.
 *
 * <p>
 * Removed everything related to entity-vs-entity collisions; only collisions
 * against the world's blocks are kept.
 *
 * <p>
 * Bounding boxes are represented as 6 raw doubles (minX, minY, minZ, maxX,
 * maxY, maxZ)
 * for the moving body, but block colliders are plain {@link BoundingBox}
 * instances in
 * world coordinates, gathered from {@link Block#getCollisionShape()} (Paper
 * API) via
 * {@link VoxelShape#getBoundingBoxes()}. Movement resolution per axis
 * ("Shapes.collide")
 * is implemented as a manual swept-AABB clamp against those boxes.
 */
public final class Collide {

   private Collide() {
   }

   public static Vector collide(final Mob mob, final Vector movement) {
      double minX = mob.getMinX();
      double minY = mob.getMinY();
      double minZ = mob.getMinZ();
      double maxX = mob.getMaxX();
      double maxY = mob.getMaxY();
      double maxZ = mob.getMaxZ();

      World world = mob.getWorld();

      Vector movementStep = movement.lengthSquared() == 0.0
            ? movement
            : collideBoundingBox(world, movement, minX, minY, minZ, maxX, maxY, maxZ);

      boolean xCollision = movement.getX() != movementStep.getX();
      boolean yCollision = movement.getY() != movementStep.getY();
      boolean zCollision = movement.getZ() != movementStep.getZ();
      boolean onGroundAfterCollision = yCollision && movement.getY() < 0.0;

      float maxUpStep = mob.getMaxUpStep();

      if (maxUpStep > 0.0F && (onGroundAfterCollision || mob.onGround()) && (xCollision || zCollision)) {
         double groundedMinX = minX;
         double groundedMinY = minY;
         double groundedMinZ = minZ;
         double groundedMaxX = maxX;
         double groundedMaxY = maxY;
         double groundedMaxZ = maxZ;

         if (onGroundAfterCollision) {
            groundedMinY += movementStep.getY();
            groundedMaxY += movementStep.getY();
         }

         double[] stepUp = expand(groundedMinX, groundedMinY, groundedMinZ, groundedMaxX, groundedMaxY, groundedMaxZ,
               movement.getX(), maxUpStep, movement.getZ());

         if (!onGroundAfterCollision) {
            stepUp = expand(stepUp[0], stepUp[1], stepUp[2], stepUp[3], stepUp[4], stepUp[5], 0.0, -1.0E-5, 0.0);
         }

         List<BoundingBox> colliders = collectColliders(world, stepUp[0], stepUp[1], stepUp[2], stepUp[3], stepUp[4],
               stepUp[5]);
         float stepHeightToSkip = (float) movementStep.getY();
         float[] candidateStepUpHeights = collectCandidateStepUpHeights(groundedMinY, colliders, maxUpStep,
               stepHeightToSkip);

         for (float candidateStepUpHeight : candidateStepUpHeights) {
            Vector stepFromGround = collideWithShapes(
                  new Vector(movement.getX(), candidateStepUpHeight, movement.getZ()),
                  groundedMinX, groundedMinY, groundedMinZ, groundedMaxX, groundedMaxY, groundedMaxZ,
                  colliders);

            double stepHorizSqr = stepFromGround.getX() * stepFromGround.getX()
                  + stepFromGround.getZ() * stepFromGround.getZ();
            double movementHorizSqr = movementStep.getX() * movementStep.getX()
                  + movementStep.getZ() * movementStep.getZ();

            if (stepHorizSqr > movementHorizSqr) {
               double distanceToGround = minY - groundedMinY;
               return new Vector(stepFromGround.getX(), stepFromGround.getY() - distanceToGround,
                     stepFromGround.getZ());
            }
         }
      }

      return movementStep;
   }

   private static float[] collectCandidateStepUpHeights(
         final double boundingBoxMinY, final List<BoundingBox> colliders, final float maxStepHeight,
         final float stepHeightToSkip) {
      TreeSet<Float> candidates = new TreeSet<>();

      for (BoundingBox collider : colliders) {
         for (double coord : new double[] { collider.getMinY(), collider.getMaxY() }) {
            float relativeCoord = (float) (coord - boundingBoxMinY);
            if (!(relativeCoord < 0.0F) && relativeCoord != stepHeightToSkip && relativeCoord <= maxStepHeight) {
               candidates.add(relativeCoord);
            }
         }
      }

      float[] sortedCandidates = new float[candidates.size()];
      int i = 0;
      for (float candidate : candidates) {
         sortedCandidates[i++] = candidate;
      }

      return sortedCandidates;
   }

   public static Vector collideBoundingBox(
         final World world, final Vector movement,
         final double minX, final double minY, final double minZ,
         final double maxX, final double maxY, final double maxZ) {
      double[] expanded = expand(minX, minY, minZ, maxX, maxY, maxZ, movement.getX(), movement.getY(), movement.getZ());
      List<BoundingBox> colliders = collectColliders(world, expanded[0], expanded[1], expanded[2], expanded[3],
            expanded[4], expanded[5]);
      return collideWithShapes(movement, minX, minY, minZ, maxX, maxY, maxZ, colliders);
   }

   /**
    * Gathers world-space {@link BoundingBox} colliders for every block whose
    * collision
    * shape intersects the given region. Requires the Paper API
    * ({@link Block#getCollisionShape()}); on plain Spigot this method is
    * unavailable.
    */
   private static List<BoundingBox> collectColliders(
         final World world,
         final double minX, final double minY, final double minZ,
         final double maxX, final double maxY, final double maxZ) {
      List<BoundingBox> colliders = new ArrayList<>();

      int minBlockX = (int) Math.floor(minX);
      int minBlockY = (int) Math.floor(minY);
      int minBlockZ = (int) Math.floor(minZ);
      int maxBlockX = (int) Math.floor(maxX);
      int maxBlockY = (int) Math.floor(maxY);
      int maxBlockZ = (int) Math.floor(maxZ);

      for (int x = minBlockX; x <= maxBlockX; x++) {
         for (int y = minBlockY; y <= maxBlockY; y++) {
            for (int z = minBlockZ; z <= maxBlockZ; z++) {
               Block block = world.getBlockAt(x, y, z);
               VoxelShape shape = block.getCollisionShape();

               for (BoundingBox local : shape.getBoundingBoxes()) {
                  colliders.add(local.clone().shift(x, y, z));
               }
            }
         }
      }

      return colliders;
   }

   private static Vector collideWithShapes(
         final Vector movement,
         final double minX, final double minY, final double minZ,
         final double maxX, final double maxY, final double maxZ,
         final List<BoundingBox> colliders) {
      if (colliders.isEmpty()) {
         return movement;
      } else {
         Vector resolved = new Vector(0.0, 0.0, 0.0);

         for (Axis axis : axisStepOrder(movement)) {
            double axisMovement = getAxis(movement, axis);
            if (axisMovement != 0.0) {
               double[] moved = move(minX, minY, minZ, maxX, maxY, maxZ, resolved.getX(), resolved.getY(),
                     resolved.getZ());
               double collision = collide(axis, moved[0], moved[1], moved[2], moved[3], moved[4], moved[5], colliders,
                     axisMovement);
               resolved = setAxis(resolved, axis, collision);
            }
         }

         return resolved;
      }
   }

   /**
    * Clamps {@code movement} along {@code axis} so the moving box (defined by
    * min/max X/Y/Z) does not penetrate any of the given world-space colliders.
    * This replaces vanilla's {@code Shapes.collide(axis, box, shapes, movement)}.
    */
   private static double collide(
         final Axis axis,
         final double minX, final double minY, final double minZ,
         final double maxX, final double maxY, final double maxZ,
         final List<BoundingBox> colliders, double movement) {
      for (BoundingBox collider : colliders) {
         if (movement == 0.0) {
            break;
         }

         double colMinX = collider.getMinX();
         double colMinY = collider.getMinY();
         double colMinZ = collider.getMinZ();
         double colMaxX = collider.getMaxX();
         double colMaxY = collider.getMaxY();
         double colMaxZ = collider.getMaxZ();

         switch (axis) {
            case X -> {
               if (maxY > colMinY && minY < colMaxY && maxZ > colMinZ && minZ < colMaxZ) {
                  if (movement > 0.0 && colMinX >= maxX) {
                     movement = Math.min(movement, colMinX - maxX);
                  } else if (movement < 0.0 && colMaxX <= minX) {
                     movement = Math.max(movement, colMaxX - minX);
                  }
               }
            }
            case Y -> {
               if (maxX > colMinX && minX < colMaxX && maxZ > colMinZ && minZ < colMaxZ) {
                  if (movement > 0.0 && colMinY >= maxY) {
                     movement = Math.min(movement, colMinY - maxY);
                  } else if (movement < 0.0 && colMaxY <= minY) {
                     movement = Math.max(movement, colMaxY - minY);
                  }
               }
            }
            case Z -> {
               if (maxX > colMinX && minX < colMaxX && maxY > colMinY && minY < colMaxY) {
                  if (movement > 0.0 && colMinZ >= maxZ) {
                     movement = Math.min(movement, colMinZ - maxZ);
                  } else if (movement < 0.0 && colMaxZ <= minZ) {
                     movement = Math.max(movement, colMaxZ - minZ);
                  }
               }
            }
         }
      }

      return movement;
   }

   private static double getAxis(final Vector vector, final Axis axis) {
      return switch (axis) {
         case X -> vector.getX();
         case Y -> vector.getY();
         case Z -> vector.getZ();
      };
   }

   private static Vector setAxis(final Vector vector, final Axis axis, final double value) {
      return switch (axis) {
         case X -> new Vector(value, vector.getY(), vector.getZ());
         case Y -> new Vector(vector.getX(), value, vector.getZ());
         case Z -> new Vector(vector.getX(), vector.getY(), value);
      };
   }

   /**
    * Order in which axes are resolved when colliding with shapes. The axis with
    * the
    * smallest movement is resolved first, which avoids clipping into corners when
    * moving diagonally. NOTE: this is a simplified approximation of vanilla's
    * Direction.axisStepOrder and may need tuning.
    */
   private static List<Axis> axisStepOrder(final Vector movement) {
      List<Axis> order = new ArrayList<>(List.of(Axis.X, Axis.Y, Axis.Z));

      order.sort((axis1, axis2) -> {
         // Priorité 1 : Si le mouvement va vers le haut (Step-up ou saut),
         // Y doit être résolu en premier pour passer au-dessus de l'obstacle.
         if (movement.getY() > 0.0) {
            if (axis1 == Axis.Y)
               return -1;
            if (axis2 == Axis.Y)
               return 1;
         }

         // Priorité 2 : Sinon, trier par magnitude (du plus petit au plus grand)
         // Cela évite le "corner clipping" sur les axes X et Z en diagonale.
         return Double.compare(Math.abs(getAxis(movement, axis1)), Math.abs(getAxis(movement, axis2)));
      });

      return order;
   }

   private static double[] expand(
         final double minX, final double minY, final double minZ,
         final double maxX, final double maxY, final double maxZ,
         final double dx, final double dy, final double dz) {
      return new double[] {
            minX + Math.min(dx, 0.0),
            minY + Math.min(dy, 0.0),
            minZ + Math.min(dz, 0.0),
            maxX + Math.max(dx, 0.0),
            maxY + Math.max(dy, 0.0),
            maxZ + Math.max(dz, 0.0)
      };
   }

   private static double[] move(
         final double minX, final double minY, final double minZ,
         final double maxX, final double maxY, final double maxZ,
         final double dx, final double dy, final double dz) {
      return new double[] { minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz };
   }

   public enum Axis {
      X, Y, Z
   }
}