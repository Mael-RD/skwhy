package skwhy.pathfinder.navigation;

import skwhy.pathfinder.Mob;
import skwhy.pathfinder.pathcalculator.PathFinder;
import skwhy.pathfinder.Navigation.PathfindingType;

import org.bukkit.util.Vector;
import org.bukkit.block.Block;

public class WaterBoundPathNavigation extends PathNavigation {
   private boolean allowBreaching;

   public WaterBoundPathNavigation(final Mob mob) {
      super(mob);
   }

   @Override
   protected PathFinder createPathFinder() {
      this.allowBreaching = false;
      return new PathFinder(mob, PathfindingType.SWIM);
   }

   @Override
   protected boolean canUpdatePath() {
      return this.allowBreaching || this.mob.isInLiquid();
   }

   @Override
   protected Vector getTempMobPos() {
      return new Vector(this.mob.getX(), this.mob.getY()+0.5, this.mob.getZ());
   }

   @Override
   protected double getGroundY(final Vector target) {
      return target.getY();
   }

   @Override
   protected boolean canMoveDirectly(final Vector startPos, final Vector stopPos) {
      return isClearForMovementBetween(this.mob, startPos, stopPos, false);
   }

   @Override
   public boolean isStableDestination(final Block block) {
      return !block.getType().isSolid();
   }

   @Override
   public void setCanFloat(final boolean canFloat) {
   }

   @Override
   public boolean canNavigateGround() {
      return false;
   }
}
