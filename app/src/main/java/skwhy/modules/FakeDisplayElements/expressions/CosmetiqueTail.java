package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.CosmetiqueData;

/**
 * Expression Skript pour accéder et modifier les paramètres de la queue
 * dans une instance de CosmetiqueData.
 *
 * Patterns:
 * 0  rigidity                      → contrôle la raideur du ressort
 * 1  damping                        → contrôle l'amortissement
 * 2  velocity smoothing             → lissage de la vélocité
 * 3  velocity [influence] forward   → influence vitesse avant/arrière
 * 4  velocity [influence] lateral   → influence vitesse gauche/droite
 * 5  velocity [influence] vertical  → influence vitesse verticale
 * 6  velocity [influence] yaw       → influence rotation yaw
 * 7  max deflection angle           → angle maximal de déflexion
 * 8  depth deflection factor        → amplification par profondeur
 * 9  impulse [influence] forward    → impulsion choc avant/arrière
 * 10 impulse [influence] lateral    → impulsion choc gauche/droite
 * 11 impulse [influence] vertical   → impulsion choc vertical
 * 12 undulation [amplitude] x       → amplitude ondulation axe X
 * 13 undulation [amplitude] y       → amplitude ondulation axe Y
 * 14 undulation [amplitude] z       → amplitude ondulation axe Z
 * 15 undulation frequency           → fréquence de l'ondulation
 * 16 undulation propagation         → propagation de l'ondulation entre segments
 * 17 random [amplitude]             → amplitude du mouvement aléatoire
 * 18 random frequency               → fréquence du mouvement aléatoire
 */
public class CosmetiqueTail extends SimpleExpression<Number> {

    private int matchedPattern;
    private Expression<CosmetiqueData> cosmetiqueExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.matchedPattern = matchedPattern;
        this.cosmetiqueExpr = (Expression<CosmetiqueData>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Number[] get(Event event) {
        CosmetiqueData c = cosmetiqueExpr.getSingle(event);
        if (c == null) return null;

        Number value = switch (matchedPattern) {
            case 0 -> c.getTailRigidity();
            case 1 -> c.getTailDamping();
            case 2 -> c.getTailVelocitySmoothing();
            case 3 -> c.getTailVelocityInfluenceForward();
            case 4 -> c.getTailVelocityInfluenceLateral();
            case 5 -> c.getTailVelocityInfluenceVertical();
            case 6 -> c.getTailVelocityInfluenceYaw();
            case 7 -> c.getTailMaxDeflectionAngle();
            case 8 -> c.getTailDepthDeflectionFactor();
            case 9 -> c.getTailImpulseInfluenceForward();
            case 10 -> c.getTailImpulseInfluenceLateral();
            case 11 -> c.getTailImpulseInfluenceVertical();
            case 12 -> c.getTailUndulationAmplitudeX();
            case 13 -> c.getTailUndulationAmplitudeY();
            case 14 -> c.getTailUndulationAmplitudeZ();
            case 15 -> c.getTailUndulationFrequency();
            case 16 -> c.getTailUndulationPropagation();
            case 17 -> c.getTailRandomAmplitude();
            case 18 -> c.getTailRandomFrequency();
            default -> null;
        };

        return value != null ? new Number[]{ value } : null;
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ Number.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || !(delta[0] instanceof Number n)) return;
        CosmetiqueData c = cosmetiqueExpr.getSingle(event);
        if (c == null) return;

        switch (matchedPattern) {
            case 0 -> c.setTailRigidity(n.floatValue());
            case 1 -> c.setTailDamping(n.floatValue());
            case 2 -> c.setTailVelocitySmoothing(n.floatValue());
            case 3 -> c.setTailVelocityInfluenceForward(n.floatValue());
            case 4 -> c.setTailVelocityInfluenceLateral(n.floatValue());
            case 5 -> c.setTailVelocityInfluenceVertical(n.floatValue());
            case 6 -> c.setTailVelocityInfluenceYaw(n.floatValue());
            case 7 -> c.setTailMaxDeflectionAngle(n.floatValue());
            case 8 -> c.setTailDepthDeflectionFactor(n.floatValue());
            case 9 -> c.setTailImpulseInfluenceForward(n.floatValue());
            case 10 -> c.setTailImpulseInfluenceLateral(n.floatValue());
            case 11 -> c.setTailImpulseInfluenceVertical(n.floatValue());
            case 12 -> c.setTailUndulationAmplitudeX(n.floatValue());
            case 13 -> c.setTailUndulationAmplitudeY(n.floatValue());
            case 14 -> c.setTailUndulationAmplitudeZ(n.floatValue());
            case 15 -> c.setTailUndulationFrequency(n.floatValue());
            case 16 -> c.setTailUndulationPropagation(n.floatValue());
            case 17 -> c.setTailRandomAmplitude(n.floatValue());
            case 18 -> c.setTailRandomFrequency(n.floatValue());
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Number> getReturnType() { return Number.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return switch (matchedPattern) {
            case 0 -> "tail rigidity";
            case 1 -> "tail damping";
            case 2 -> "tail velocity smoothing";
            case 3 -> "tail velocity [influence] forward";
            case 4 -> "tail velocity [influence] lateral";
            case 5 -> "tail velocity [influence] vertical";
            case 6 -> "tail velocity [influence] yaw";
            case 7 -> "tail max deflection angle";
            case 8 -> "tail depth deflection factor";
            case 9 -> "tail impulse [influence] forward";
            case 10 -> "tail impulse [influence] lateral";
            case 11 -> "tail impulse [influence] vertical";
            case 12 -> "tail undulation [amplitude] x";
            case 13 -> "tail undulation [amplitude] y";
            case 14 -> "tail undulation [amplitude] z";
            case 15 -> "tail undulation frequency";
            case 16 -> "tail undulation propagation";
            case 17 -> "tail random [amplitude]";
            case 18 -> "tail random frequency";
            default -> null;
        };
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(CosmetiqueTail.class, Number.class)
                .addPattern("tail rigidity of %cosmetique%")                                  // 0
                .addPattern("tail damping of %cosmetique%")                                   // 1
                .addPattern("tail velocity smoothing of %cosmetique%")                        // 2
                .addPattern("tail velocity [influence] forward of %cosmetique%")             // 3
                .addPattern("tail velocity [influence] lateral of %cosmetique%")             // 4
                .addPattern("tail velocity [influence] vertical of %cosmetique%")            // 5
                .addPattern("tail velocity [influence] yaw of %cosmetique%")                 // 6
                .addPattern("tail max deflection angle of %cosmetique%")                     // 7
                .addPattern("tail depth deflection factor of %cosmetique%")                  // 8
                .addPattern("tail impulse [influence] forward of %cosmetique%")              // 9
                .addPattern("tail impulse [influence] lateral of %cosmetique%")              // 10
                .addPattern("tail impulse [influence] vertical of %cosmetique%")             // 11
                .addPattern("tail undulation [amplitude] x of %cosmetique%")                 // 12
                .addPattern("tail undulation [amplitude] y of %cosmetique%")                 // 13
                .addPattern("tail undulation [amplitude] z of %cosmetique%")                 // 14
                .addPattern("tail undulation frequency of %cosmetique%")                     // 15
                .addPattern("tail undulation propagation of %cosmetique%")                   // 16
                .addPattern("tail random [amplitude] of %cosmetique%")                       // 17
                .addPattern("tail random frequency of %cosmetique%")                         // 18
                .build()
        );
    }
}
