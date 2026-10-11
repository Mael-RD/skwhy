package skwhy.chunkpath.gate;

import skwhy.chunkpath.Direction;

import java.util.HashSet;
import java.util.Set;

/**
 * Lie deux chunks adjacents déjà analysés : pour chaque porte du bord commun, on trouve
 * la ou les portes du chunk voisin qui partagent au moins un bloc adjacent, puis on
 * élague les culs-de-sac qui restent des culs-de-sac une fois le voisin pris en compte.
 */
public final class ChunkGateLinker {

    private ChunkGateLinker() {
    }

    /**
     * @param a          chunk source
     * @param b          chunk voisin, doit être adjacent à `a` dans la direction `dirAtoB`
     * @param dirAtoB    direction de a vers b (ex: EAST si b.chunkX == a.chunkX + 1)
     */
    public static void link(ChunkGateData a, ChunkGateData b, Direction dirAtoB) {
        if (b.chunkX != a.chunkX + dirAtoB.dx || b.chunkZ != a.chunkZ + dirAtoB.dz) {
            throw new IllegalArgumentException("Les chunks fournis ne sont pas adjacents dans la direction donnée");
        }
        Direction dirBtoA = dirAtoB.opposite();

        for (Gate ga : a.onEdge(dirAtoB)) {
            Set<Long> keysA = borderKeys(ga, dirAtoB);
            for (Gate gb : b.onEdge(dirBtoA)) {
                Set<Long> keysB = borderKeys(gb, dirBtoA);
                if (hasCommon(keysA, keysB)) {
                    ga.neighborLinks.add(gb.globalId());
                    gb.neighborLinks.add(ga.globalId());
                }
            }
        }

        // IMPORTANT : les deux décisions d'élagage sont calculées à partir du même état
        // (celui juste après la liaison ci-dessus), AVANT toute suppression. Si on élaguait
        // côté `a` puis évaluait `b` ensuite, `b` pourrait encore compter un lien vers une
        // porte de `a` déjà supprimée et fausser son propre comptage de carrefour.
        Set<Integer> toRemoveFromA = candidatesToPrune(a, b);
        Set<Integer> toRemoveFromB = candidatesToPrune(b, a);
        a.prune(toRemoveFromA);
        b.prune(toRemoveFromB);
    }

    /**
     * Une porte "cul-de-sac candidat" (composante interne ne touchant qu'elle-même) est
     * définitivement élaguée si, une fois le voisin analysé, elle ne mène toujours qu'à
     * au plus une seule porte de l'autre côté (aucun carrefour révélé par le voisin).
     */
    private static Set<Integer> candidatesToPrune(ChunkGateData self, ChunkGateData neighbor) {
        Set<Integer> toRemove = new HashSet<>();
        String neighborPrefix = neighbor.chunkX + "," + neighbor.chunkZ + ",";
        for (Gate g : self.gates()) {
            if (!g.deadEndCandidate) continue;
            long distinctNeighborGates = g.neighborLinks.stream()
                    .filter(id -> id.startsWith(neighborPrefix))
                    .distinct()
                    .count();
            if (distinctNeighborGates <= 1) {
                toRemove.add(g.id);
            } else {
                g.deadEndCandidate = false; // carrefour confirmé, on la garde
            }
        }
        return toRemove;
    }

    /**
     * Clé de correspondance "de l'autre côté de la frontière" pour un bloc de bord :
     * pour EST/OUEST on compare (y,z), pour NORD/SUD on compare (y,x). La coordonnée le
     * long de l'axe perpendiculaire à la frontière est volontairement ignorée puisque
     * les deux chunks ont chacun leur bloc de bord fixe sur cet axe.
     */
    private static Set<Long> borderKeys(Gate gate, Direction edgeOfGate) {
        Set<Long> keys = new HashSet<>();
        for (int[] block : gate.memberBlocksWorld) {
            int y = block[1];
            int cross = (edgeOfGate == Direction.EAST || edgeOfGate == Direction.WEST) ? block[2] : block[0];
            keys.add(((long) y << 32) ^ (cross & 0xFFFFFFFFL));
        }
        return keys;
    }

    private static boolean hasCommon(Set<Long> a, Set<Long> b) {
        Set<Long> small = a.size() <= b.size() ? a : b;
        Set<Long> large = a.size() <= b.size() ? b : a;
        for (long k : small) {
            if (large.contains(k)) return true;
        }
        return false;
    }
}
