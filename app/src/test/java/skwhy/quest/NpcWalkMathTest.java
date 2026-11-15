package skwhy.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import skwhy.quest.npc.NpcMover;
import skwhy.quest.npc.NpcWalkMath;

class NpcWalkMathTest {

    /** L en deux segments : (0,0,0) -> (10,0,0) -> (10,0,10). */
    private static NpcWalkMath.Track corner() {
        return new NpcWalkMath.Track(List.of(new double[]{0, 0, 0}, new double[]{10, 0, 0}, new double[]{10, 0, 10}));
    }

    @Test
    void trackLengthIsTheSumOfSegments() {
        assertEquals(20.0, corner().length(), 1e-9);
    }

    @Test
    void projectionGivesProgressAndDistanceToTheLine() {
        double[] p = corner().project(4, 0, 3);
        assertEquals(4.0, p[0], 1e-9);
        assertEquals(3.0, p[1], 1e-9);
        double[] second = corner().project(13, 0, 5);
        assertEquals(15.0, second[0], 1e-9);
        assertEquals(3.0, second[1], 1e-9);
    }

    @Test
    void projectionClampsBeforeTheStartAndAfterTheEnd() {
        assertEquals(0.0, corner().project(-5, 0, 0)[0], 1e-9);
        assertEquals(20.0, corner().project(10, 0, 30)[0], 1e-9);
    }

    @Test
    void shortcutAheadGivesAHigherProgress() {
        // sur un U, le joueur coupe par le milieu : son avancement est celui du point du trajet le plus proche
        NpcWalkMath.Track u = new NpcWalkMath.Track(List.of(new double[]{0, 0, 0}, new double[]{0, 0, 20}, new double[]{10, 0, 20}, new double[]{10, 0, 0}));
        double[] atEnd = u.project(10, 0, 2);
        assertTrue(atEnd[0] > 45, "le joueur est presque au bout du U: " + atEnd[0]);
    }

    @Test
    void emptyOrSinglePointTracks() {
        assertEquals(0.0, new NpcWalkMath.Track(List.of()).length());
        assertTrue(Double.isInfinite(new NpcWalkMath.Track(List.of()).project(1, 1, 1)[1]));
        assertEquals(5.0, new NpcWalkMath.Track(List.of(new double[]{0, 0, 0})).project(3, 4, 0)[1], 1e-9);
    }

    @Test
    void speedFactorFollowsTheGap() {
        assertEquals(1.0, NpcWalkMath.speedFactor(0, 15, 1.6), 1e-9);
        assertEquals(0.0, NpcWalkMath.speedFactor(15, 15, 1.6), 1e-9);
        assertEquals(0.0, NpcWalkMath.speedFactor(40, 15, 1.6), 1e-9);
        assertEquals(1.6, NpcWalkMath.speedFactor(-40, 15, 1.6), 1e-9);
        assertEquals(1.2, NpcWalkMath.speedFactor(-3, 15, 1.6), 1e-9);
        assertEquals(0.5, NpcWalkMath.speedFactor(7.5, 15, 1.6), 1e-9);
        assertEquals(1.0, NpcWalkMath.speedFactor(99, 0, 1.6), 1e-9);
    }

    @Test
    void waitsWhenAwayFromThePathOrWellAheadOfThePlayer() {
        assertFalse(NpcWalkMath.shouldWait(false, 5, 2, 15, 0.6));
        assertTrue(NpcWalkMath.shouldWait(false, 16, 2, 15, 0.6), "trop loin devant le joueur");
        assertTrue(NpcWalkMath.shouldWait(false, -5, 20, 15, 0.6), "le joueur est parti du chemin");
        assertFalse(NpcWalkMath.shouldWait(false, -30, 3, 15, 0.6), "le joueur a pris de l'avance : le NPC le rattrape");
    }

    @Test
    void resumesOnlyWithAMargin() {
        assertTrue(NpcWalkMath.shouldWait(true, 12, 2, 15, 0.6), "encore trop loin pour repartir");
        assertFalse(NpcWalkMath.shouldWait(true, 8, 2, 15, 0.6));
        assertTrue(NpcWalkMath.shouldWait(true, 0, 10, 15, 0.6), "toujours hors du chemin");
    }

    @Test
    void modesAcceptTheOldNames() {
        assertEquals(NpcMover.Mode.CONSTANT, NpcMover.Mode.parse("constant"));
        assertEquals(NpcMover.Mode.CONSTANT, NpcMover.Mode.parse("walk"));
        assertEquals(NpcMover.Mode.WAIT, NpcMover.Mode.parse("wait"));
        assertEquals(NpcMover.Mode.WAIT, NpcMover.Mode.parse("follow"));
        assertEquals(NpcMover.Mode.INSTANT, NpcMover.Mode.parse(null));
        assertEquals(NpcMover.Mode.INSTANT, NpcMover.Mode.parse("n'importe quoi"));
    }

    @Test
    void ruleVariablesAreNormalised() {
        assertEquals("pathfinding::rules::villes", NpcMover.normalize("pathfinding::rules::villes"));
        assertEquals("pathfinding::rules::villes", NpcMover.normalize("{pathfinding::rules::villes::*}"));
        assertEquals("pathfinding::rules::villes", NpcMover.normalize(" pathfinding::rules::villes::* "));
        assertEquals("", NpcMover.normalize(null));
    }
}
