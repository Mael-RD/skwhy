package skwhy.chunkpath.move.navigation;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import skwhy.chunkpath.block.PathCostRules;
import skwhy.chunkpath.move.LongMob;
import skwhy.chunkpath.move.pathcalculator.LongPathFinder;
import skwhy.chunkpath.move.pathcalculator.Path;
import skwhy.chunkpath.move.pathcalculator.Node;
import skwhy.chunkpath.move.pathcalculator.PathType;

/**
 * Ground navigation adapted from {@code skwhy.pathfinder.navigation.GroundPathNavigation}: same
 * surface-finding/trim logic, but {@link #createPath} no longer just logs-and-refuses when the
 * target chunk isn't loaded — it records the reason via {@link #lastFailureReason} so the owning
 * {@code LongNavigation} state machine can tell an unloaded-chunk failure apart from any other
 * pathfinding failure and react accordingly (wait, then teleport-catch-up), instead of treating
 * every failure the same way.
 */
public class LongGroundPathNavigation extends PathNavigation {
   private boolean avoidSun;
   private boolean canPathToTargetsBelowSurface;
   private final PathCostRules costRules;

   public enum FailureReason { NONE, UNLOADED_CHUNK, OTHER }
   private FailureReason lastFailureReason = FailureReason.NONE;

   public LongGroundPathNavigation(final LongMob mob, final PathCostRules costRules) {
      super(mob);
      this.costRules = costRules;
   }

   public FailureReason lastFailureReason() {
      return lastFailureReason;
   }

   @Override
   protected LongPathFinder createPathFinder() {
      return new LongPathFinder(mob, costRules);
   }

   @Override
   protected boolean canUpdatePath() {
      return this.mob.onGround() || this.mob.isInWater();
   }

   @Override
   protected Vector getTempMobPos() {
      final Location loc = this.mob.getLocation();
      return new Vector(loc.getX(), this.getSurfaceY(), loc.getZ());
   }

   @Override
   public Path createPath(Location location, float reachRange) {

      // Checked by chunk coordinates, never via Location#getChunk() — that call force-loads the
      // chunk synchronously, which would silently defeat the "never force-load" design this
      // long-distance system relies on (LongNavigation handles unloaded chunks itself via a
      // wait-then-teleport-catch-up strategy instead).
      if (!mob.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
         this.lastFailureReason = FailureReason.UNLOADED_CHUNK;
         return null;
      } else {
         this.lastFailureReason = FailureReason.NONE;
         final Chunk chunk = location.getChunk(); // safe now: confirmed loaded above

         if (!this.canPathToTargetsBelowSurface) {
            location = this.findSurfacePosition(chunk, location, reachRange);
         }

         Path result = super.createPath(location, reachRange);
         if (result == null) {
            this.lastFailureReason = FailureReason.OTHER;
         }
         return result;
      }
   }

   final Location findSurfacePosition(final Chunk chunk, Location location, float reachRange) {
      final World world = mob.getWorld();
      Location pos = location.clone();

      if (world.getBlockAt(pos).getType().isAir()) {

         while (pos.getY() >= world.getMinHeight() && world.getBlockAt(pos).getType().isAir()) {
            pos.add(0, -1, 0);
         }

         if (pos.getY() >= world.getMinHeight()) {
            return pos;
         }

         pos.setY(location.getY()+1);

         while (pos.getY() <= world.getMaxHeight() -1 && world.getBlockAt(pos).getType().isAir()) {
            pos.add(0, 1, 0);
         }
         
      }

      if (!world.getBlockAt(pos).getType().isSolid()) {
         return pos;
      } else {
         pos.add(0, 1, 0);

         while (pos.getY() <= world.getMaxHeight() -1 && world.getBlockAt(pos).getType().isSolid()) {
            pos.add(0, 1, 0);
         }

         return pos;
      }
   }

   private int getSurfaceY() {
      if (this.mob.isInWater() && this.canFloat()) {
         final World world = this.mob.getWorld();
         final Location loc = this.mob.getLocation();
         int surface = loc.getBlockY();
         Block block = world.getBlockAt(loc.getBlockX(), surface, loc.getBlockZ());
         int steps = 0;

         while (block.getType() == Material.WATER) {
            block = world.getBlockAt(loc.getBlockX(), ++surface, loc.getBlockZ());
            if (++steps > 16) {
               return loc.getBlockY();
            }
         }

         return surface;
      } else {
         return (int) Math.floor(this.mob.getLocation().getY() + 0.5);
      }
   }

   @Override
   protected void trimPath() {
      super.trimPath();
      if (this.avoidSun) {
         final World world = this.mob.getWorld();
         final Location loc = this.mob.getLocation();

         if (canSeeSky(world.getBlockAt(loc.getBlockX(), (int) Math.floor(loc.getY() + 0.5), loc.getBlockZ()))) {
            return;
         }

         for (int i = 0; i < this.path.getNodeCount(); i++) {
            Node node = this.path.getNode(i);
            if (canSeeSky(world.getBlockAt(node.x, node.y, node.z))) {
               this.path.truncateNodes(i);
               return;
            }
         }
      }
   }

   private boolean canSeeSky(final Block block) {
      return block.getLightFromSky() > 0;
   }

   @Override
   public boolean canNavigateGround() {
      return true;
   }

   protected boolean hasValidPathType(final PathType pathType) {
      if (pathType == PathType.WATER) {
         return false;
      } else {
         return pathType == PathType.LAVA ? false : pathType != PathType.OPEN;
      }
   }

   public void setAvoidSun(final boolean avoidSun) {
      this.avoidSun = avoidSun;
   }

   public void setCanWalkOverFences(final boolean canWalkOverFences) {
      this.nodeEvaluator.setCanWalkOverFences(canWalkOverFences);
   }

   public void setCanPathToTargetsBelowSurface(final boolean canPathToTargetsBelowSurface) {
      this.canPathToTargetsBelowSurface = canPathToTargetsBelowSurface;
   }

   @Override
   public String toString() {
      return super.toString();
   }
}