package skwhy.pathfinder.navigation;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import skwhy.pathfinder.Mob;
import skwhy.pathfinder.pathcalculator.PathFinder;
import skwhy.pathfinder.pathcalculator.Path;
import skwhy.pathfinder.pathcalculator.Node;
import skwhy.pathfinder.pathcalculator.PathType;
import skwhy.pathfinder.Navigation.PathfindingType;

public class GroundPathNavigation extends PathNavigation {
   private boolean avoidSun;
   private boolean canPathToTargetsBelowSurface;

   public GroundPathNavigation(final Mob mob) {
      super(mob);
   }

   @Override
   protected PathFinder createPathFinder() {
      return new PathFinder(mob, PathfindingType.WALK);
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
   public Path createPath(Vector pos, float reachRange) {
      final World world = this.mob.getWorld();
      final int chunkX = pos.getBlockX() >> 4;
      final int chunkZ = pos.getBlockX() >> 4;

      if (!world.isChunkLoaded(chunkX, chunkZ)) {
         return null;
      } else {
         final Chunk chunk = world.getChunkAt(chunkX, chunkZ);

         if (!this.canPathToTargetsBelowSurface) {
            pos = this.findSurfacePosition(chunk, pos, reachRange);
         }

         return super.createPath(pos, reachRange);
      }
   }

   final Vector findSurfacePosition(final Chunk chunk, Vector pos, float reachRange) {
      final World world = mob.getWorld();
      final int x = pos.getBlockX();
      final int z = pos.getBlockZ();
      int y = pos.getBlockY();
      Block block = world.getBlockAt(x,y,z);

      if (block.getType().isAir()) {
         int columnY = y - 1;

         while (columnY >= world.getMinHeight() && world.getBlockAt(x, columnY, z).getType().isAir()) {
            columnY--;
         }

         if (columnY >= world.getMinHeight()) {
            return new Vector(x, columnY + 1, z);
         }

         columnY = y + 1;

         while (columnY <= world.getMaxHeight() - 1 && world.getBlockAt(x, columnY, z).getType().isAir()) {
            columnY++;
         }

         block = world.getBlockAt(x, columnY, z);
         y = columnY;
      }

      if (!block.getType().isSolid()) {
         return new Vector(block.getX(), block.getY(), block.getZ());
      } else {
         int columnY = y + 1;

         while (columnY <= world.getMaxHeight() - 1 && world.getBlockAt(x, columnY, z).getType().isSolid()) {
            columnY++;
         }

         return new Vector(x, columnY, z);
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
}