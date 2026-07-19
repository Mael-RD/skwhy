package skwhy.pathfinder.control;

import skwhy.pathfinder.Mth;

public interface Control {
   default float rotateTowards(final float fromAngle, final float toAngle, final float maxRot) {
      float diff = Mth.degreesDifference(fromAngle, toAngle);
      float diffClamped = java.lang.Math.clamp(diff, -maxRot, maxRot); 
      return fromAngle + diffClamped;
   }
}
