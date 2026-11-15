package skwhy.chunkpath.longnav;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import skwhy.chunkpath.longnav.NavigationGuard.Entry;

class NavigationGuardTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    private static Entry real(int id) {
        return new Entry(id, true, Set.of());
    }

    private static Entry virtual(int id, UUID... viewers) {
        return new Entry(id, false, Set.of(viewers));
    }

    @Test
    void freeEntityIsAllowed() {
        assertNull(NavigationGuard.refusal(real(1), List.of(real(2), virtual(3, ALICE))));
        assertNull(NavigationGuard.refusal(virtual(1, ALICE), List.of()));
    }

    @Test
    void onlyOneRealNavigationPerEntity() {
        assertNotNull(NavigationGuard.refusal(real(1), List.of(real(1))));
    }

    @Test
    void noRealNavigationOverVirtualOnes() {
        assertNotNull(NavigationGuard.refusal(real(1), List.of(virtual(1, ALICE))));
    }

    @Test
    void noVirtualNavigationOverARealOne() {
        assertNotNull(NavigationGuard.refusal(virtual(1, ALICE), List.of(real(1))));
    }

    @Test
    void severalVirtualNavigationsNeedDistinctPlayers() {
        assertNull(NavigationGuard.refusal(virtual(1, BOB), List.of(virtual(1, ALICE))));
        assertNotNull(NavigationGuard.refusal(virtual(1, ALICE, BOB), List.of(virtual(1, ALICE))));
    }

    @Test
    void virtualNavigationWithoutPlayersNeverClashesWithAnotherVirtualOne() {
        assertNull(NavigationGuard.refusal(virtual(1), List.of(virtual(1, ALICE))));
    }
}
