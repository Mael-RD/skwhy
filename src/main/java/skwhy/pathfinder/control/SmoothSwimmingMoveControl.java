package skwhy.pathfinder.control;

import org.bukkit.util.Vector;

import skwhy.pathfinder.Mth;
import skwhy.pathfinder.Mob;

public class SmoothSwimmingMoveControl extends MoveControl {
   private final int maxTurnX;
   private final int maxTurnY;
   private final boolean applyGravity;

   public SmoothSwimmingMoveControl(
      final Mob mob,
      final int maxTurnX,
      final int maxTurnY,
      final boolean applyGravity
   ) {
      super(mob);
      this.maxTurnX = maxTurnX;
      this.maxTurnY = maxTurnY;
      this.applyGravity = applyGravity;
   }

   @Override
   public void tick() {
      if (this.applyGravity && this.mob.isInWater()) {
         this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(new Vector(0.0, 0.005, 0.0)));
      }

      if (this.operation == Operation.MOVE_TO && !this.mob.getNavigation().isDone()) {
         double xd = this.wantedX - this.mob.getX();
         double yd = this.wantedY - this.mob.getY();
         double zd = this.wantedZ - this.mob.getZ();
         double dd = xd * xd + yd * yd + zd * zd;
         if (dd < 2.5000003E-7F) {
            this.mob.setZza(0.0F);
         } else {
            float yRotD = (float)(Math.atan2(zd, xd) * 180.0F / (float)Math.PI) - 90.0F;
            this.mob.setYaw(this.rotlerp(this.mob.getYaw(), yRotD, this.maxTurnY));
            this.mob.yBodyRot = this.mob.getYaw();
            this.mob.yHeadRot = this.mob.getYaw();
            if (this.mob.isInWater()) {
               double sqrt = Math.sqrt(xd * xd + zd * zd);
               if (Math.abs(yd) > 1.0E-5F || Math.abs(sqrt) > 1.0E-5F) {
                  float xRotD = -((float)(Math.atan2(yd, sqrt) * 180.0F / (float)Math.PI));
                  xRotD = Math.clamp(Mth.wrapDegrees(xRotD), (float)(-this.maxTurnX), (float)this.maxTurnX);
                  this.mob.setPitch(this.rotateTowards(this.mob.getPitch(), xRotD, 5.0F));
               }

               float cos = (float)Math.cos(this.mob.getPitch() * (Math.PI / 180.0));
               float sin = (float)Math.sin(this.mob.getPitch() * (Math.PI / 180.0));
               this.mob.zza = cos * mob.getSpeed();
               this.mob.yya = -sin * mob.getSpeed();
            }
         }
      } else {
         this.mob.setSpeed(0.0F);
         this.mob.setXxa(0.0F);
         this.mob.setYya(0.0F);
         this.mob.setZza(0.0F);
      }
   }
}
