package skwhy.pathfinder.control;

import skwhy.pathfinder.Mob;

public class FlyingMoveControl extends MoveControl {
   private final int maxTurn;
   private final boolean hoversInPlace;

   public FlyingMoveControl(final Mob mob, final int maxTurn, final boolean hoversInPlace) {
      super(mob);
      this.maxTurn = maxTurn;
      this.hoversInPlace = hoversInPlace;
   }

   @Override
   public void tick() {
      if (this.operation == Operation.MOVE_TO) {
         this.operation = Operation.WAIT;
         this.mob.setNoGravity(true);
         double xd = this.wantedX - this.mob.getX();
         double yd = this.wantedY - this.mob.getY();
         double zd = this.wantedZ - this.mob.getZ();
         double dd = xd * xd + yd * yd + zd * zd;
         if (dd < 2.5000003E-7F) {
            this.mob.setYya(0.0F);
            this.mob.setZza(0.0F);
            return;
         }

         float yRotD = (float)(Math.atan2(zd, xd) * 180.0F / (float)Math.PI) - 90.0F;
         this.mob.setYRot(this.rotlerp(this.mob.getYRot(), yRotD, 90.0F));

         this.mob.setSpeed(this.mob.getSpeed());
         double sd = Math.sqrt(xd * xd + zd * zd);
         if (Math.abs(yd) > 1.0E-5F || Math.abs(sd) > 1.0E-5F) {
            float xRotD = (float)(-(Math.atan2(yd, sd) * 180.0F / (float)Math.PI));
            this.mob.setXRot(this.rotlerp(this.mob.getXRot(), xRotD, this.maxTurn));
            this.mob.setYya(yd > 0.0 ? this.mob.getSpeed() : -this.mob.getSpeed());
         }
      } else {
         if (!this.hoversInPlace) {
            this.mob.setNoGravity(false);
         }

         this.mob.setYya(0.0F);
         this.mob.setZza(0.0F);
      }
   }
}
