package skwhy.pathfinder.navigation;

import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import skwhy.pathfinder.Mob;
import skwhy.pathfinder.pathcalculator.PathFinder;
import skwhy.pathfinder.Navigation.PathfindingType;

public class FlyingPathNavigation extends PathNavigation {
   public FlyingPathNavigation(final Mob mob) {
      super(mob);
   }

   @Override
   protected PathFinder createPathFinder() {
      return new PathFinder(mob, PathfindingType.FLY);
   }

   @Override
   protected boolean canMoveDirectly(final Vector startPos, final Vector stopPos) {
      return isClearForMovementBetween(this.mob, startPos, stopPos, true);
   }

   @Override
   protected boolean canUpdatePath() {
      return true;
   }

   @Override
   protected Vector getTempMobPos() {
      return this.mob.getLocation().toVector();
   }

   @Override
   public void tick() {
      this.tick++;
      if (this.hasDelayedRecomputation) {
         this.recomputePath();
      }

      if (!this.isDone()) {
         if (this.canUpdatePath()) {
            this.followThePath();
         } else if (this.path != null && !this.path.isDone()) {
            Vector pos = this.path.getNextEntityPos(this.mob);
            if (this.mob.getLocation().getBlockX() == Math.floor(pos.getX()) && this.mob.getLocation().getBlockY() == Math.floor(pos.getY()) && this.mob.getLocation().getBlockZ() == Math.floor(pos.getZ())) {
               this.path.advance();
            }
         }

         if (!this.isDone()) {
            Vector target = this.path.getNextEntityPos(this.mob);
            this.mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ());
         }
      }
   }

   @Override
   public boolean isStableDestination(final Block pos) {
      return !pos.isPassable();
   }

   @Override
   public boolean canNavigateGround() {
      return false;
   }
}