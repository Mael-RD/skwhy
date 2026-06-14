package skwhy.pathfinder.control;

import java.util.Optional;
import skwhy.pathfinder.Mth;
import skwhy.pathfinder.Mob;


import org.bukkit.util.Vector;

public class LookControl implements Control {
   protected final Mob mob;
   protected float yMaxRotSpeed;
   protected float xMaxRotAngle;
   protected int lookAtCooldown;
   protected double wantedX;
   protected double wantedY;
   protected double wantedZ;

   public LookControl(final Mob mob) {
      this.mob = mob;
   }

   public void setLookAt(final Vector vec) {
      this.setLookAt(vec.getX(), vec.getY(), vec.getZ());
   }

   public void setLookAt(final double x, final double y, final double z) {
      this.setLookAt(x, y, z, this.mob.getHeadRotSpeed(), this.mob.getMaxHeadXRot());
   }

   public void setLookAt(final double x, final double y, final double z, final float yMaxRotSpeed, final float xMaxRotAngle) {
      this.wantedX = x;
      this.wantedY = y;
      this.wantedZ = z;
      this.yMaxRotSpeed = yMaxRotSpeed;
      this.xMaxRotAngle = xMaxRotAngle;
      this.lookAtCooldown = 2;
   }

   public void tick() {
      if (this.resetXRotOnTick()) {
         this.mob.setXRot(0.0F);
      }

      if (this.lookAtCooldown > 0) {
         this.lookAtCooldown--;
         this.getYRotD().ifPresent(yRotD -> this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, yRotD, this.yMaxRotSpeed));
         this.getXRotD().ifPresent(xRotD -> this.mob.setXRot(this.rotateTowards(this.mob.getXRot(), xRotD, this.xMaxRotAngle)));
      } else {
         this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, this.mob.yBodyRot, 10.0F);
      }

      this.clampHeadRotationToBody();
   }

   protected void clampHeadRotationToBody() {
      if (!this.mob.getNavigation().isDone()) {
         this.mob.yHeadRot = Mth.rotateIfNecessary(this.mob.yHeadRot, this.mob.yBodyRot, this.mob.getMaxHeadYRot());
      }
   }

   protected boolean resetXRotOnTick() {
      return true;
   }

   public boolean isLookingAtTarget() {
      return this.lookAtCooldown > 0;
   }

   public double getWantedX() {
      return this.wantedX;
   }

   public double getWantedY() {
      return this.wantedY;
   }

   public double getWantedZ() {
      return this.wantedZ;
   }

   protected Optional<Float> getXRotD() {
      double xd = this.wantedX - mob.getX();
      double yd = this.wantedY - mob.getY() - mob.getHitbox().getY() * 0.9;
      double zd = this.wantedZ - mob.getZ();
      double sd = Math.sqrt(xd * xd + zd * zd);
      return !(Math.abs(yd) > 1.0E-5F) && !(Math.abs(sd) > 1.0E-5F) ? Optional.empty() : Optional.of((float)(-(Math.atan2(yd, sd) * 180.0F / (float)Math.PI)));
   }

   protected Optional<Float> getYRotD() {
      double xd = this.wantedX - mob.getX();
      double zd = this.wantedZ - mob.getZ();
      return !(Math.abs(zd) > 1.0E-5F) && !(Math.abs(xd) > 1.0E-5F)
         ? Optional.empty()
         : Optional.of((float)(Math.atan2(zd, xd) * 180.0F / (float)Math.PI) - 90.0F);
   }
}
