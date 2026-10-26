package skwhy.chunkpath.gate;

import skwhy.chunkpath.Direction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Une porte = un groupe de blocs de bord (même bord de chunk) connectés directement entre
 * eux (voisins d'un pas, de proche en proche) : un bord continu ne forme qu'une seule porte.
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

    /**
     * Coût de la paire (cette porte, porte voisine liée) : coût du chemin au sol qui va du bloc central
     * de cette porte au bloc central de l'autre en franchissant la frontière (clé = identifiant global
     * de la porte voisine, comme dans {@link #neighborLinks}). Absent tant qu'il n'a pas été calculé
     * (base écrite avant cette donnée, chunk illisible, pas de chemin) : l'appelant se rabat alors sur
     * une estimation par la distance.
     */
    public final Map<String, Integer> neighborCosts = new HashMap<>();

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
