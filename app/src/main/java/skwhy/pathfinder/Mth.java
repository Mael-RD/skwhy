package skwhy.pathfinder;

import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.Location;

public class Mth {

   public static float degreesDifference(final float fromAngle, final float toAngle) {
      return wrapDegrees(toAngle - fromAngle);
   }

   public static float wrapDegrees(final float angle) {
      float normalizedAngle = angle % 360.0F;
      if (normalizedAngle >= 180.0F) {
         normalizedAngle -= 360.0F;
      }

      if (normalizedAngle < -180.0F) {
         normalizedAngle += 360.0F;
      }

      return normalizedAngle;
   }

   public static float rotateIfNecessary(final float baseAngle, final float targetAngle, final float maxAngleDiff) {
      return targetAngle - Math.clamp(degreesDifference(baseAngle, targetAngle), -maxAngleDiff, maxAngleDiff);
   }

   public static boolean isVectorFinite(Vector vector) {
      if (vector == null) {
         return false;
      }

      return Double.isFinite(vector.getX()) &&
            Double.isFinite(vector.getY()) &&
            Double.isFinite(vector.getZ());
   }

   public static BlockFace getCardinalDirection(float yaw) {
      // On divise par 90 car chaque direction cardinale couvre 90 degrés.
      // Le "& 0x3" est une astuce binaire très rapide pour "modulo 4",
      // qui nous ramène toujours à un index entre 0 et 3, même avec des angles
      // négatifs.
      int index = Math.round(yaw / 90.0F) & 0x3;

      switch (index) {
         case 0:
            return BlockFace.SOUTH; // 0 degrés
         case 1:
            return BlockFace.WEST; // 90 degrés
         case 2:
            return BlockFace.NORTH; // 180 degrés
         case 3:
            return BlockFace.EAST; // 270 (-90) degrés
         default:
            return BlockFace.SOUTH;
      }
   }

   public static boolean closerToCenterThan(Block block, Location target, double distance) {
      Location center = block.getLocation().add(0.5, 0.5, 0.5);
      return center.distanceSquared(target) < (distance * distance);
   }
}