package skwhy.pathfinder.navigation;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import skwhy.pathfinder.Mob;
import skwhy.pathfinder.pathcalculator.PathFinder;
import skwhy.pathfinder.pathcalculator.AmphibiousNodeEvaluator;
import skwhy.pathfinder.Navigation.PathfindingType;

public class AmphibiousPathNavigation extends PathNavigation {
   public AmphibiousPathNavigation(final Mob mob) {
      super(mob);
   }

   @Override
   protected PathFinder createPathFinder() {
      this.nodeEvaluator = new AmphibiousNodeEvaluator(false);
      return new PathFinder(mob, PathfindingType.WALK_WATER);
   }

   @Override
   protected boolean canUpdatePath() {
      return true;
   }

   @Override
   protected Vector getTempMobPos() {
      final Location loc = this.mob.getLocation();
      return new Vector(loc.getX(), loc.getY() + 0.5, loc.getZ());
   }

   @Override
   protected double getGroundY(final Vector target) {
      return target.getY();
   }

   @Override
   protected boolean canMoveDirectly(final Vector startPos, final Vector stopPos) {
      return this.mob.isInWater() ? isClearForMovementBetween(this.mob, startPos, stopPos, false) : false;
   }

   @Override
   public boolean isStableDestination(final Block block) {
      return !block.getRelative(0, -1, 0).getType().isAir();
   }

   @Override
   public void setCanFloat(final boolean canFloat) {
   }

   @Override
   public boolean canNavigateGround() {
      return true;
   }
}