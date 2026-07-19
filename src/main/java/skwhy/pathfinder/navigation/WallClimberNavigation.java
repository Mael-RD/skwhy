package skwhy.pathfinder.navigation;

import org.jspecify.annotations.Nullable;
import org.bukkit.Location;
import org.bukkit.block.Block;

import skwhy.pathfinder.Mob;
import skwhy.pathfinder.pathcalculator.Path;
import skwhy.pathfinder.Mth;

public class WallClimberNavigation extends GroundPathNavigation {
   private @Nullable Block block;

   public WallClimberNavigation(final Mob mob) {
      super(mob);
   }

   @Override
   public Path createPath(final Location pos, float reachRange) {
      this.block = mob.getWorld().getBlockAt((int) pos.getX(), (int) pos.getY(), (int) pos.getZ());
      return super.createPath(pos, reachRange);
   }

   public void tick() {
      if (!this.isDone()) {
         super.tick();
      } else {
         if (this.block != null) {
            if (!Mth.closerToCenterThan(block, this.mob.getLocation(), this.mob.getHitbox().getX())
               && (
                  !(this.mob.getY() > this.block.getY())
                     || Mth.closerToCenterThan(mob.getWorld().getBlockAt(this.block.getX(), (int) this.mob.getY(), this.block.getZ()), this.mob.getLocation(), this.mob.getHitbox().getX())
               )) {
               this.mob
                  .getMoveControl()
                  .setWantedPosition(this.block.getX(), this.block.getY(), this.block.getZ());
            } else {
               this.block = null;
            }
         }
      }
   }
}
