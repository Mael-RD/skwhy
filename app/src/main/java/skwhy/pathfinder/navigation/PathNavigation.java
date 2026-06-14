package skwhy.pathfinder.navigation;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import skwhy.pathfinder.Mob;
import skwhy.pathfinder.pathcalculator.PathFinder;
import skwhy.pathfinder.pathcalculator.Path;
import skwhy.pathfinder.pathcalculator.PathType;
import skwhy.pathfinder.pathcalculator.Node;
import skwhy.pathfinder.pathcalculator.NodeEvaluator;
import skwhy.pathfinder.pathcalculator.WalkNodeEvaluator;

public abstract class PathNavigation {
   private static final int MAX_TIME_RECOMPUTE = 20;
   private static final int STUCK_CHECK_INTERVAL = 100;
   private static final float STUCK_THRESHOLD_DISTANCE_FACTOR = 0.25F;
   protected final Mob mob;
   protected final World world;
   protected @Nullable Path path;
   protected int tick;
   protected int lastStuckCheck;
   protected Vector lastStuckCheckPos = new Vector(0, 0, 0);
   protected Vector timeoutCachedNode = new Vector(0, 0, 0);
   protected long timeoutTimer;
   protected long lastTimeoutCheck;
   protected double timeoutLimit;
   protected float maxDistanceToWaypoint = 0.5F;
   protected boolean hasDelayedRecomputation;
   protected long timeLastRecompute;
   protected NodeEvaluator nodeEvaluator;
   private @Nullable Vector targetPos;
   private final PathFinder pathFinder;
   private boolean isStuck;

   public PathNavigation(final Mob mob) {
      this.mob = mob;
      this.world = mob.getWorld();
      this.pathFinder = this.createPathFinder();
   }

   public @Nullable Vector getTargetPos() {
      return this.targetPos;
   }

   protected abstract PathFinder createPathFinder();

   public void recomputePath() {
      if (this.world.getFullTime() - this.timeLastRecompute <= 20L || !this.canUpdatePath()) {
         this.hasDelayedRecomputation = true;
      } else if (this.targetPos != null) {
         this.path = null;
         this.path = this.createPath(this.targetPos, 50);
         this.timeLastRecompute = this.world.getFullTime();
         this.hasDelayedRecomputation = false;
      }
   }

   protected @Nullable Path createPath(Vector target, float maxPathLength) {
      if (this.mob.getLocation().getY() < this.world.getMinHeight()) {
         return null;
      } else if (!this.canUpdatePath()) {
         return null;
      } else if (this.path != null && !this.path.isDone() && target.equals(this.targetPos)) {
         return this.path;
      } else {
         Block fromPos = this.mob.getLocation().getBlock();
         Path path = this.pathFinder.findPath(this.mob, target, maxPathLength);
         if (path != null && path.getTarget() != null) {
            this.targetPos = path.getTarget();
            this.resetStuckTimeout();
         }

         return path;
      }
   }

   public boolean moveTo(Location location, final int reachRange) {
      Path newPath = this.createPath(new Vector(location.getX(), location.getY(), location.getZ()), reachRange);
      if (newPath == null) {
         this.path = null;
         return false;
      } else {
         if (!newPath.sameAs(this.path)) {
            this.path = newPath;
         }

         if (this.isDone()) {
            return false;
         } else {
            this.trimPath();
            if (this.path.getNodeCount() <= 0) {
               return false;
            } else {
               Vector mobPos = this.getTempMobPos();
               this.lastStuckCheck = this.tick;
               this.lastStuckCheckPos = mobPos;
               return true;
            }
         }
      }
   }

   public @Nullable Path getPath() {
      return this.path;
   }

   public void tick() {
      this.tick++;
      if (this.hasDelayedRecomputation) {
         this.recomputePath();
      }

      if (!this.isDone()) {
         if (this.canUpdatePath()) {
            this.followThePath();
         } else if (this.path != null && !this.path.isDone()) {
            Vector mobPos = this.getTempMobPos();
            Vector pos = this.path.getNextEntityPos(this.mob);
            if (mobPos.getY() > pos.getY() && !this.mob.onGround() && Math.floor(mobPos.getX()) == Math.floor(pos.getX()) && Math.floor(mobPos.getZ()) == Math.floor(pos.getZ())) {
               this.path.advance();
            }
         }

         if (!this.isDone()) {
            Vector target = this.path.getNextEntityPos(this.mob);
            this.mob.getMoveControl().setWantedPosition(target.getX(), this.getGroundY(target), target.getZ());
         }
      }
   }

   protected double getGroundY(final Vector target) {
      Block block = this.world.getBlockAt((int) Math.floor(target.getX()), (int) Math.floor(target.getY()), (int) Math.floor(target.getZ()));
      return block.getRelative(0, -1, 0).getType().isAir() ? target.getY() : WalkNodeEvaluator.getFloorLevel(this.world, block.getX(), block.getY(), block.getZ());
   }

   protected void followThePath() {
      Vector mobPos = this.getTempMobPos();
      float bbWidth = (float) this.mob.getHitbox().getX();
      this.maxDistanceToWaypoint = bbWidth > 0.75F ? bbWidth / 2.0F : 0.75F - bbWidth / 2.0F;
      Vector currentNodePos = this.path.getNextNodePos();
      double xDistance = Math.abs(this.mob.getLocation().getX() - (currentNodePos.getX() + 0.5));
      double yDistance = Math.abs(this.mob.getLocation().getY() - currentNodePos.getY());
      double zDistance = Math.abs(this.mob.getLocation().getZ() - (currentNodePos.getZ() + 0.5));
      boolean isCloseEnoughToCurrentNode = xDistance < this.maxDistanceToWaypoint && zDistance < this.maxDistanceToWaypoint && yDistance < 1.0;
      if (isCloseEnoughToCurrentNode || this.canCutCorner(this.path.getNextNode().type) && this.shouldTargetNextNodeInDirection(mobPos)) {
         this.path.advance();
      }

      this.doStuckDetection(mobPos);
   }

   private boolean shouldTargetNextNodeInDirection(final Vector mobPosition) {
      if (this.path.getNextNodeIndex() + 1 >= this.path.getNodeCount()) {
         return false;
      } else {
         Vector currentNode = atBottomCenterOf(this.path.getNextNodePos());
         if (mobPosition.distanceSquared(currentNode) >= 4.0) {
            return false;
         } else if (this.canMoveDirectly(mobPosition, this.path.getNextEntityPos(this.mob))) {
            return true;
         } else {
            Vector nextNode = atBottomCenterOf(this.path.getNodePos(this.path.getNextNodeIndex() + 1));
            Vector mobToCurrent = currentNode.clone().subtract(mobPosition);
            Vector mobToNext = nextNode.clone().subtract(mobPosition);
            double mobToCurrentSqr = mobToCurrent.lengthSquared();
            double mobToNextSqr = mobToNext.lengthSquared();
            boolean closerToNextThanCurrent = mobToNextSqr < mobToCurrentSqr;
            boolean withinCurrentBlock = mobToCurrentSqr < 0.5;
            if (!closerToNextThanCurrent && !withinCurrentBlock) {
               return false;
            } else {
               Vector mobDirection = mobToCurrent.clone().normalize();
               Vector pathDirection = mobToNext.clone().normalize();
               return pathDirection.dot(mobDirection) < 0.0;
            }
         }
      }
   }

   private static Vector atBottomCenterOf(final Vector pos) {
      return new Vector(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
   }

   protected void doStuckDetection(final Vector mobPos) {
      if (this.tick - this.lastStuckCheck > 100) {
         float effectiveSpeed = this.mob.getSpeed() >= 1.0F ? this.mob.getSpeed() : this.mob.getSpeed() * this.mob.getSpeed();
         float thresholdDistance = effectiveSpeed * 100.0F * 0.25F;
         if (mobPos.distanceSquared(this.lastStuckCheckPos) < thresholdDistance * thresholdDistance) {
            this.isStuck = true;
            this.stop();
         } else {
            this.isStuck = false;
         }

         this.lastStuckCheck = this.tick;
         this.lastStuckCheckPos = mobPos;
      }

      if (this.path != null && !this.path.isDone()) {
         Vector pos = this.path.getNextNodePos();
         long time = this.world.getFullTime();
         if (pos.equals(this.timeoutCachedNode)) {
            this.timeoutTimer = this.timeoutTimer + (time - this.lastTimeoutCheck);
         } else {
            this.timeoutCachedNode = pos;
            double distToNode = mobPos.distance(atBottomCenterOf(this.timeoutCachedNode));
            this.timeoutLimit = this.mob.getSpeed() > 0.0F ? distToNode / this.mob.getSpeed() * 20.0 : 0.0;
         }

         if (this.timeoutLimit > 0.0 && this.timeoutTimer > this.timeoutLimit * 3.0) {
            this.timeoutPath();
         }

         this.lastTimeoutCheck = time;
      }
   }

   private void timeoutPath() {
      this.resetStuckTimeout();
      this.stop();
   }

   private void resetStuckTimeout() {
      this.timeoutCachedNode = new Vector(0, 0, 0);
      this.timeoutTimer = 0L;
      this.timeoutLimit = 0.0;
      this.isStuck = false;
   }

   public boolean isDone() {
      return this.path == null || this.path.isDone();
   }

   public boolean isInProgress() {
      return !this.isDone();
   }

   public void stop() {
      this.path = null;
   }

   protected abstract Vector getTempMobPos();

   protected abstract boolean canUpdatePath();

   protected void trimPath() {
      if (this.path != null) {
         for (int i = 0; i < this.path.getNodeCount(); i++) {
            Node node = this.path.getNode(i);
            Node nextNode = i + 1 < this.path.getNodeCount() ? this.path.getNode(i + 1) : null;
            Block block = this.world.getBlockAt(node.x, node.y, node.z);
            if (isCauldron(block)) {
               this.path.replaceNode(i, node.cloneAndMove(node.x, node.y + 1, node.z));
               if (nextNode != null && node.y >= nextNode.y) {
                  this.path.replaceNode(i + 1, node.cloneAndMove(nextNode.x, node.y + 1, nextNode.z));
               }
            }
         }
      }
   }

   private static boolean isCauldron(final Block block) {
      return Tag.CAULDRONS.isTagged(block.getType());
   }

   protected boolean canMoveDirectly(final Vector startPos, final Vector stopPos) {
      return false;
   }

   public boolean canCutCorner(final PathType pathType) {
      return pathType != PathType.FIRE_IN_NEIGHBOR && pathType != PathType.DAMAGING_IN_NEIGHBOR && pathType != PathType.WALKABLE_DOOR;
   }

   protected static boolean isClearForMovementBetween(final Mob mob, final Vector startPos, final Vector stopPos, final boolean blockedByFluids) {
      World world = mob.getWorld();
      Location start = new Location(world, startPos.getX(), startPos.getY(), startPos.getZ());
      Vector to = new Vector(stopPos.getX(), stopPos.getY() + mob.getHitbox().getY() * 0.5, stopPos.getZ());
      Vector direction = to.clone().subtract(startPos);
      double distance = direction.length();
      if (distance == 0.0) {
         return true;
      }

      direction.normalize();
      FluidCollisionMode fluidMode = blockedByFluids ? FluidCollisionMode.ALWAYS : FluidCollisionMode.NEVER;
      RayTraceResult result = world.rayTraceBlocks(start, direction, distance, fluidMode, true);
      return result == null;
   }

   public boolean isStableDestination(final Block pos) {
      Block below = pos.getRelative(0, -1, 0);
      return below.getType().isOccluding();
   }

   public NodeEvaluator getNodeEvaluator() {
      return this.nodeEvaluator;
   }

   public void setCanFloat(final boolean canFloat) {
      this.nodeEvaluator.setCanFloat(canFloat);
   }

   public boolean canFloat() {
      return this.nodeEvaluator.canFloat();
   }

   public boolean shouldRecomputePath(final Block pos) {
      if (this.hasDelayedRecomputation) {
         return false;
      } else if (this.path != null && !this.path.isDone() && this.path.getNodeCount() != 0) {
         Node target = this.path.getEndNode();
         Vector middlePos = new Vector((target.x + this.mob.getLocation().getX()) / 2.0, (target.y + this.mob.getLocation().getY()) / 2.0, (target.z + this.mob.getLocation().getZ()) / 2.0);
         double remainingNodes = this.path.getNodeCount() - this.path.getNextNodeIndex();
         return closerToCenterThan(pos, middlePos, remainingNodes);
      } else {
         return false;
      }
   }

   private static boolean closerToCenterThan(final Block pos, final Vector point, final double distance) {
      Vector center = new Vector(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
      return center.distanceSquared(point) < distance * distance;
   }

   public float getMaxDistanceToWaypoint() {
      return this.maxDistanceToWaypoint;
   }

   public boolean isStuck() {
      return this.isStuck;
   }

   public abstract boolean canNavigateGround();

   public void setCanOpenDoors(final boolean canOpenDoors) {
      this.nodeEvaluator.setCanOpenDoors(canOpenDoors);
   }
}