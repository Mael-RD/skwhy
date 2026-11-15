package skwhy.modules.RandomStuff.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.util.PathSmoothing;

import java.util.ArrayList;
import java.util.List;

@Name("Smoothed Locations")
@Description({
    "Turns a list of locations (a polyline) into a list of new locations placed along the same route, "
        + "separated by exactly the given distance (straight-line distance between two consecutive locations).",
    "The first location of the list is the first returned location. The last location is never returned: "
        + "the list stops just before it.",
    "The yaw and pitch of every returned location follow the direction of the route at that point.",
    "The last number is the smoothing level, as a distance in blocks: 0 gives straight lines between the points "
        + "(sharp angles). Otherwise the route is averaged (gaussian filter) over about that distance on each side, so the curve "
        + "does NOT go through every location: several successive points are blended together. "
        + "A staircase of locations becomes a straight slope with a high value instead of waves. "
        + "The first and the last locations stay fixed.",
    "Returns nothing when there are less than two different locations, when the locations are in different worlds "
        + "or when the distance is not strictly positive (with a warning)."
})
@Examples({
    "set {_pts::*} to location(0, 64, 0), location(10, 64, 0) and location(10, 64, 10)",
    "# straight lines, a location every 1 block",
    "set {_line::*} to smoothed locations from {_pts::*} with distance 1 and smooth 0",
    "# rounded corner",
    "set {_curve::*} to smoothed locations from {_pts::*} with distance 0.5 and smooth 4",
    "broadcast \"%size of {_curve::*}% locations\"",
    "broadcast \"yaw of the first one: %yaw of {_curve::1}%\""
})
@Since("1.6.0")
public class SmoothedLocations extends SimpleExpression<Location> {

    private static final double EPS = 1e-9;
    private static final int MAX_RESULT = 1_000_000;

    private Expression<Location> pointsExpr;
    private Expression<Number> distanceExpr;
    private Expression<Number> smoothExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        pointsExpr = (Expression<Location>) exprs[0];
        distanceExpr = (Expression<Number>) exprs[1];
        smoothExpr = (Expression<Number>) exprs[2];
        return true;
    }

    @Override
    protected @Nullable Location[] get(Event event) {
        Location[] locs = pointsExpr.getArray(event);
        Number dist = distanceExpr.getSingle(event);
        Number smooth = smoothExpr.getSingle(event);
        if (dist == null || smooth == null || locs.length < 2) return null;

        double d = dist.doubleValue();
        if (!(d > 0) || Double.isInfinite(d)) {
            Skript.warning("smoothed locations : la distance doit être strictement positive");
            return null;
        }
        double radius = Math.max(0, smooth.doubleValue());

        World world = locs[0].getWorld();
        List<Vector> pts = new ArrayList<>();
        for (Location l : locs) {
            if (l.getWorld() != world) {
                Skript.warning("smoothed locations : les locations ne sont pas dans le même monde");
                return null;
            }
            Vector v = l.toVector();
            if (pts.isEmpty() || pts.get(pts.size() - 1).distanceSquared(v) > EPS) pts.add(v);
        }
        if (pts.size() < 2) return null;

        List<Vector> curve = buildCurve(pts, radius);
        return sample(curve, d, world);
    }

    /** Polyligne lissée ({@link PathSmoothing}) ; rayon nul : les points tels quels. */
    private static List<Vector> buildCurve(List<Vector> pts, double sigma) {
        PathSmoothing.Result r = PathSmoothing.smooth(pts, sigma, false);
        if (r == null) return pts;
        List<Vector> clean = new ArrayList<>();
        for (Vector v : r.curve()) {
            if (clean.isEmpty() || clean.get(clean.size() - 1).distanceSquared(v) > EPS) clean.add(v);
        }
        return clean;
    }

    /** Marche sur la polyligne : chaque point est à la distance (à vol d'oiseau) {@code d} du précédent. */
    private static @Nullable Location[] sample(List<Vector> curve, double d, World world) {
        List<Location> result = new ArrayList<>();
        Vector current = curve.get(0).clone();
        int seg = 0; // le point courant est sur le segment seg -> seg+1
        result.add(at(world, current, curve, seg));
        Vector end = curve.get(curve.size() - 1);

        while (result.size() < MAX_RESULT) {
            Vector found = null;
            int foundSeg = seg;
            for (int i = seg; i < curve.size() - 1; i++) {
                Vector a = i == seg ? current : curve.get(i);
                Vector b = curve.get(i + 1);
                Vector ab = b.clone().subtract(a);
                double len2 = ab.lengthSquared();
                if (len2 < EPS) continue;
                // |a + t*ab - current| = d, t dans [0, 1], plus grande racine
                Vector ac = a.clone().subtract(current);
                double bq = ac.dot(ab), cq = ac.lengthSquared() - d * d;
                double disc = bq * bq - len2 * cq;
                if (disc < 0) continue;
                double t = (-bq + Math.sqrt(disc)) / len2;
                if (t >= 0 && t <= 1 + 1e-12) {
                    found = a.clone().add(ab.multiply(Math.min(t, 1)));
                    foundSeg = i;
                    break;
                }
            }
            // rien trouvé, ou le point tombe sur l'arrivée : on s'arrête avant la dernière location
            if (found == null || found.distanceSquared(end) < 1e-6) break;
            current = found;
            seg = foundSeg;
            result.add(at(world, current, curve, seg));
        }
        return result.toArray(new Location[0]);
    }

    private static Location at(World world, Vector pos, List<Vector> curve, int seg) {
        Vector dir = curve.get(Math.min(seg + 1, curve.size() - 1)).clone().subtract(pos);
        if (dir.lengthSquared() < EPS) {
            dir = curve.get(curve.size() - 1).clone().subtract(curve.get(Math.max(0, curve.size() - 2)));
        }
        Location l = pos.toLocation(world);
        if (dir.lengthSquared() > EPS) l.setDirection(dir);
        return l;
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends Location> getReturnType() { return Location.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "smoothed locations from " + pointsExpr.toString(event, debug)
            + " with distance " + distanceExpr.toString(event, debug)
            + " and smooth " + smoothExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(SmoothedLocations.class, Location.class)
                .addPattern("smoothed locations from %locations% with distance %number% and smooth %number%")
                .build()
        );
    }
}
