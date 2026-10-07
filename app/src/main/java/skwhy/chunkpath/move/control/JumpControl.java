package skwhy.chunkpath.move.control;

import skwhy.chunkpath.move.LongMob;

public class JumpControl implements Control {
   private final LongMob mob;
   protected boolean jump;

   public JumpControl(final LongMob mob) {
      this.mob = mob;
   }

   public void jump() {
      this.jump = true;
   }

   public void tick() {
      this.mob.setJumping(this.jump);
      this.jump = false;
   }
}
