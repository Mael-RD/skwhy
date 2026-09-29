package skwhy.chunkpath.gate;

import skwhy.chunkpath.Direction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Une porte = un groupe de blocs de bord (même bord de chunk) reliés entre eux par un
 * chemin interne pondéré de coût inférieur au seuil de fusion (par défaut 20), fusion
 * transitive incluse (A-C=15 et C-B=15 fusionnent A,B,C même si A-B direct vaut 30).
 */
public final class Gate {

    public final int chunkX, chunkZ;
    public final int id; // unique seulement au sein du chunk (chunkX,chunkZ)
    public final Direction edge;

    /** Coordonnées MONDE (x,y,z) de tous les blocs de bord appartenant à cette porte. */
    public final List<int[]> memberBlocksWorld;

    /** Coordonnées MONDE du bloc le plus centré du groupe : sert de point de calcul final. */
    public final int[] representativeWorld;

    /**
     * true si, à l'intérieur de CE chunk, la composante connexe de cette porte ne touche
     * aucune autre porte (cul-de-sac local). Sujet à réévaluation lors de l'analyse du
     * chunk voisin correspondant : si côté voisin cette porte s'avère reliée à plusieurs
     * portes distinctes, elle est quand même conservée (carrefour utile).
     */
    public boolean deadEndCandidate;

    /** distance pondérée vers chaque autre porte de CE MÊME chunk (gateId -> coût). */
    public final Map<Integer, Integer> internalDistances = new HashMap<>();

    /**
     * Une fois liée à un chunk voisin : identifiants globaux ("cx,cz,gateId") des portes
     * voisines connectées (au moins un bloc adjacent de part et d'autre de la frontière).
     * Le coût de franchissement de la frontière elle-même n'est pas stocké ici : c'est
     * une simple connectivité, le coût de la traversée est à intégrer par l'algorithme
     * d'assemblage du chemin final (typiquement : coût du sol du premier bloc côté voisin).
     */
    public final Set<String> neighborLinks = new HashSet<>();

    Gate(int chunkX, int chunkZ, int id, Direction edge, List<int[]> memberBlocksWorld, int[] representativeWorld) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.id = id;
        this.edge = edge;
        this.memberBlocksWorld = memberBlocksWorld;
        this.representativeWorld = representativeWorld;
    }

    public String globalId() {
        return chunkX + "," + chunkZ + "," + id;
    }
}
