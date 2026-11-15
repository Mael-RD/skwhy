package skwhy.chunkpath.longnav;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Sécurité « une seule navigation par entité » (logique pure).
 *
 * <ul>
 *   <li>Une navigation <b>réelle</b> (elle déplace l'entité) est seule sur son entité : ni autre réelle, ni virtuelle.</li>
 *   <li>Plusieurs navigations <b>virtuelles</b> (packets seulement, pour des joueurs précis) peuvent coexister sur la
 *       même entité si elles n'ont aucun joueur en commun, et seulement s'il n'y a pas de navigation réelle.</li>
 * </ul>
 * Désactivée par {@code long_navigation.allow_multiple}.
 */
public final class NavigationGuard {

    /** Ce qu'il faut savoir d'une navigation pour la comparer. */
    public record Entry(int entityId, boolean driving, Set<UUID> viewers) {}

    private NavigationGuard() {}

    /**
     * @return {@code null} si la navigation est permise, sinon la raison du refus
     */
    public static String refusal(Entry candidate, Collection<Entry> existing) {
        for (Entry other : existing) {
            if (other.entityId() != candidate.entityId()) continue;
            if (candidate.driving()) {
                return other.driving()
                        ? "cette entité est déjà déplacée par une long navigation"
                        : "cette entité a déjà une long navigation virtuelle (une navigation réelle serait en conflit)";
            }
            if (other.driving()) return "cette entité est déjà déplacée par une long navigation réelle";
            for (UUID viewer : candidate.viewers()) {
                if (other.viewers().contains(viewer)) {
                    return "une autre long navigation virtuelle de cette entité a déjà le joueur " + viewer;
                }
            }
        }
        return null;
    }

    /** Levée quand la sécurité refuse la création (ou l'ajout d'un joueur à) une navigation. */
    public static final class Refused extends IllegalStateException {
        public Refused(String message) {
            super(message);
        }
    }
}
