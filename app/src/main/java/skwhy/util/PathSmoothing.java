package skwhy.util;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Smoothing of a polyline: the route is resampled finely along its length then averaged by a gaussian filter of standard
 * deviation {@code sigma} blocks, so the curve does not go through every vertex (a staircase becomes a slope). The two ends
 * stay fixed (central symmetry beyond each end). Used by the {@code smoothed locations} expression and by the walk of a
 * long navigation, so both follow exactly the same curve.
 */
public final class PathSmoothing {

    private static final double EPS = 1e-9;
    private static final int MAX_SAMPLES = 20000;

    private PathSmoothing() {}

    /**
     * The smoothed samples, evenly spaced along the original route ({@link #step()} apart before smoothing).
     *
     * @param curve        samples from the first vertex to the last one
     * @param vertexIndex  for each vertex of the original polyline, the index in {@code curve} of the sample at the same place
     */
    public record Result(List<Vector> curve, int[] vertexIndex, double step) {}

    /**
     * @param xzOnly only x and z are smoothed, y keeps the value of the original route at the same place
     * @return null when the route has no length or {@code sigma} is not positive
     */
    public static Result smooth(List<Vector> pts, double sigma, boolean xzOnly) {
        if (!(sigma > EPS) || pts.size() < 2) return null;
        double[] cum = new double[pts.size()];
        for (int i = 1; i < pts.size(); i++) cum[i] = cum[i - 1] + pts.get(i - 1).distance(pts.get(i));
        double total = cum[pts.size() - 1];
        if (total < EPS) return null;

        double h = Math.max(Math.min(0.1, sigma / 10), total / MAX_SAMPLES);
        int n = Math.max(1, (int) Math.ceil(total / h));
        h = total / n;
        Vector[] dense = new Vector[n + 1];
        int seg = 0;
        for (int i = 0; i <= n; i++) {
            double at = i * h;
            while (seg < pts.size() - 2 && at > cum[seg + 1]) seg++;
            double segLen = cum[seg + 1] - cum[seg];
            double t = segLen < EPS ? 1 : Math.min(1, (at - cum[seg]) / segLen);
            Vector a = pts.get(seg), b = pts.get(seg + 1);
            dense[i] = a.clone().add(b.clone().subtract(a).multiply(t));
        }

        int k = Math.max(1, (int) Math.ceil(3 * sigma / h));
        double[] w = new double[k + 1];
        double sum = 0;
        for (int j = 0; j <= k; j++) {
            w[j] = Math.exp(-(j * h) * (j * h) / (2 * sigma * sigma));
            sum += j == 0 ? w[j] : 2 * w[j];
        }
        List<Vector> out = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            Vector acc = new Vector();
            for (int j = -k; j <= k; j++) acc.add(extended(dense, i + j).multiply(w[Math.abs(j)]));
            acc.multiply(1 / sum);
            if (xzOnly) acc.setY(dense[i].getY());
            out.add(acc);
        }
        out.set(0, dense[0].clone());
        out.set(n, dense[n].clone());

        int[] vertexIndex = new int[pts.size()];
        for (int i = 0; i < pts.size(); i++) vertexIndex[i] = (int) Math.min(n, Math.round(cum[i] / h));
        return new Result(out, vertexIndex, h);
    }

    /** Sample i, extended outside [0, n] by central symmetry around each end. */
    private static Vector extended(Vector[] d, int i) {
        int n = d.length - 1;
        if (i < 0) return d[0].clone().multiply(2).subtract(extended(d, -i));
        if (i > n) return d[n].clone().multiply(2).subtract(extended(d, 2 * n - i));
        return d[i].clone();
    }
}
