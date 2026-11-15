package skwhy.quest.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NpcPlacesTest {

    @TempDir
    File dir;

    @BeforeEach
    void setUp() {
        NpcPlaces.useFile(new File(dir, "quest-npc-positions.yml"));
        NpcPlaces.clearMemory();
    }

    @AfterEach
    void tearDown() {
        NpcPlaces.useFile(null);
        NpcPlaces.clearMemory();
    }

    private static NpcPlaces.Place place(double x) {
        return new NpcPlaces.Place("world", x, 64.0, -3.5, 90f, -10f);
    }

    @Test
    void setThenGet() {
        assertTrue(NpcPlaces.set("maire", 1, place(1)));
        assertEquals(place(1), NpcPlaces.get("maire", 1));
        assertNull(NpcPlaces.get("maire", 2));
        assertNull(NpcPlaces.get("autre", 1));
    }

    @Test
    void settingTheSamePlaceChangesNothing() {
        assertTrue(NpcPlaces.set("maire", 1, place(1)));
        assertFalse(NpcPlaces.set("maire", 1, place(1)));
        assertTrue(NpcPlaces.set("maire", 1, place(2)));
    }

    @Test
    void positionsAreSorted() {
        NpcPlaces.set("maire", 3, place(3));
        NpcPlaces.set("maire", 1, place(1));
        NpcPlaces.set("maire", 2, place(2));
        assertEquals(List.of(1, 2, 3), NpcPlaces.positions("maire"));
        assertEquals(List.of(), NpcPlaces.positions("inconnu"));
    }

    @Test
    void removeForgetsThePosition() {
        NpcPlaces.set("maire", 1, place(1));
        NpcPlaces.set("maire", 2, place(2));
        assertTrue(NpcPlaces.remove("maire", 1));
        assertFalse(NpcPlaces.remove("maire", 1));
        assertEquals(List.of(2), NpcPlaces.positions("maire"));
        assertTrue(NpcPlaces.remove("maire", 2));
        assertEquals(List.of(), NpcPlaces.positions("maire"));
    }

    @Test
    void placesSurviveAReloadFromTheFile() {
        NpcPlaces.set("maire", 1, place(1));
        NpcPlaces.set("maire", 2, new NpcPlaces.Place("villes", 10.5, 70.0, 20.25, 180f, 15f));
        NpcPlaces.clearMemory();
        assertNull(NpcPlaces.get("maire", 1));
        NpcPlaces.load();
        assertEquals(place(1), NpcPlaces.get("maire", 1));
        assertEquals(new NpcPlaces.Place("villes", 10.5, 70.0, 20.25, 180f, 15f), NpcPlaces.get("maire", 2));
    }

    @Test
    void npcIdsWithDotsAreKept() {
        NpcPlaces.set("ville.maire", 1, place(1));
        NpcPlaces.clearMemory();
        NpcPlaces.load();
        assertEquals(place(1), NpcPlaces.get("ville.maire", 1));
    }

    @Test
    void missingFileLoadsNothing() {
        NpcPlaces.load();
        assertEquals(List.of(), NpcPlaces.positions("maire"));
    }
}
