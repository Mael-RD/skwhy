package skwhy.quest.npc;

import java.util.List;

/**
 * Calculs purs du déplacement d'un NPC pour un joueur (sans Bukkit, testables) : avancement le long du trajet et facteur
 * de vitesse du mode {@code wait}.
 */
public final class NpcWalkMath {

    /** Trajet à suivre : une ligne brisée avec la longueur cumulée de chaque sommet. */
    public static final class Track {
        private final double[] x;
        private final double[] y;
        private final double[] z;
        private final double[] cumulative;

        public Track(List<double[]> points) {
            int n = points.size();
            x = new double[n];
            y = new double[n];
            z = new double[n];
            cumulative = new double[n];
            for (int i = 0; i < n; i++) {
                double[] p = points.get(i);
                x[i] = p[0];
                y[i] = p[1];
                z[i] = p[2];
                if (i > 0) cumulative[i] = cumulative[i - 1] + Math.sqrt(sq(x[i] - x[i - 1]) + sq(y[i] - y[i - 1]) + sq(z[i] - z[i - 1]));
            }
        }

        public double length() {
            return cumulative.length == 0 ? 0 : cumulative[cumulative.length - 1];
        }

        /**
         * Avancement du point le plus proche de {@code (px, py, pz)} sur tout le trajet (en blocs depuis le départ) et
         * distance à ce point. Le trajet entier est cherché : un joueur qui prend un raccourci, s'éloigne puis rejoint le
         * chemin plus loin a l'avancement du point où il se trouve.
         *
         * @return {@code {avancement, distance}}
         */
        public double[] project(double px, double py, double pz) {
            if (cumulative.length == 0) return new double[]{0, Double.POSITIVE_INFINITY};
            if (cumulative.length == 1) return new double[]{0, Math.sqrt(sq(px - x[0]) + sq(py - y[0]) + sq(pz - z[0]))};
            double best = Double.POSITIVE_INFINITY;
            double progress = 0;
            for (int i = 0; i + 1 < cumulative.length; i++) {
                double dx = x[i + 1] - x[i];
                double dy = y[i + 1] - y[i];
                double dz = z[i + 1] - z[i];
                double len2 = dx * dx + dy * dy + dz * dz;
                double t = len2 < 1e-12 ? 0 : ((px - x[i]) * dx + (py - y[i]) * dy + (pz - z[i]) * dz) / len2;
                t = Math.max(0, Math.min(1, t));
                double d2 = sq(px - (x[i] + dx * t)) + sq(py - (y[i] + dy * t)) + sq(pz - (z[i] + dz * t));
                if (d2 < best) {
                    best = d2;
                    progress = cumulative[i] + (cumulative[i + 1] - cumulative[i]) * t;
                }
            }
            return new double[]{progress, Math.sqrt(best)};
        }
    }

    private NpcWalkMath() {}

    /**
     * Facteur de vitesse d'un NPC en mode {@code wait}.
     *
     * @param gap       avancement du NPC moins celui du joueur, en blocs (négatif : le NPC est derrière le joueur)
     * @param scale     écart (blocs) à partir duquel le NPC s'arrête (facteur 0) ; à l'écart nul le facteur vaut 1
     * @param maxFactor facteur maximal quand le NPC est loin derrière le joueur
     */
    public static double speedFactor(double gap, double scale, double maxFactor) {
        if (scale <= 0) return 1;
        return Math.max(0, Math.min(maxFactor, 1 - gap / scale));
    }

    /**
     * Le NPC attend-il ? Il attend quand le joueur est trop loin du trajet pour y être (il est parti ailleurs), ou trop
     * en arrière. Une fois en attente il ne repart qu'avec une marge ({@code resumeRatio} de l'écart d'arrêt).
     */
    public static boolean shouldWait(boolean waiting, double gap, double offPath, double scale, double resumeRatio) {
        double factor = waiting ? resumeRatio : 1.0;
        return offPath > scale * factor || gap > scale * factor;
    }

    private static double sq(double v) {
        return v * v;
    }
}
