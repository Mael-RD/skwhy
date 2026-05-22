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
 * 3  velocity influence             → influence de la vélocité sur la déflexion
 * 4  max deflection angle           → angle maximal de déflexion
 * 5  depth deflection factor        → amplification par profondeur
 * 6  undulation amplitude           → amplitude de l'ondulation
 * 7  undulation frequency           → fréquence de l'ondulation
 * 8  undulation propagation         → propagation de l'ondulation entre segments
 * 9  random amplitude               → amplitude du mouvement aléatoire
 * 10 random frequency               → fréquence du mouvement aléatoire
 * 11 vertical velocity influence    → influence de la vélocité verticale
 * 12 max vertical deflection angle  → angle maximal de déflexion verticale
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
            case 3 -> c.getTailVelocityInfluence();
            case 4 -> c.getTailMaxDeflectionAngle();
            case 5 -> c.getTailDepthDeflectionFactor();
            case 6 -> c.getTailUndulationAmplitude();
            case 7 -> c.getTailUndulationFrequency();
            case 8 -> c.getTailUndulationPropagation();
            case 9 -> c.getTailRandomAmplitude();
            case 10 -> c.getTailRandomFrequency();
            case 11 -> c.getTailVerticalVelocityInfluence();
            case 12 -> c.getTailMaxVerticalDeflectionAngle();
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
            case 3 -> c.setTailVelocityInfluence(n.floatValue());
            case 4 -> c.setTailMaxDeflectionAngle(n.floatValue());
            case 5 -> c.setTailDepthDeflectionFactor(n.floatValue());
            case 6 -> c.setTailUndulationAmplitude(n.floatValue());
            case 7 -> c.setTailUndulationFrequency(n.floatValue());
            case 8 -> c.setTailUndulationPropagation(n.floatValue());
            case 9 -> c.setTailRandomAmplitude(n.floatValue());
            case 10 -> c.setTailRandomFrequency(n.floatValue());
            case 11 -> c.setTailVerticalVelocityInfluence(n.floatValue());
            case 12 -> c.setTailMaxVerticalDeflectionAngle(n.floatValue());
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Number> getReturnType() { return Number.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        CosmetiqueData c = cosmetiqueExpr.getSingle(event);
        if (c == null) return null;
        return switch (matchedPattern) {
            case 0 -> Float.toString(c.getTailRigidity());
            case 1 -> Float.toString(c.getTailDamping());
            case 2 -> Float.toString(c.getTailVelocitySmoothing());
            case 3 -> Float.toString(c.getTailVelocityInfluence());
            case 4 -> Float.toString(c.getTailMaxDeflectionAngle());
            case 5 -> Float.toString(c.getTailDepthDeflectionFactor());
            case 6 -> Float.toString(c.getTailUndulationAmplitude());
            case 7 -> Float.toString(c.getTailUndulationFrequency());
            case 8 -> Float.toString(c.getTailUndulationPropagation());
            case 9 -> Float.toString(c.getTailRandomAmplitude());
            case 10 -> Float.toString(c.getTailRandomFrequency());
            case 11 -> Float.toString(c.getTailVerticalVelocityInfluence());
            case 12 -> Float.toString(c.getTailMaxVerticalDeflectionAngle());
            default -> null;
        };
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(CosmetiqueTail.class, Number.class)
                .addPattern("[the] tail rigidity of %cosmetique%")              // 0
                .addPattern("[the] tail damping of %cosmetique%")               // 1
                .addPattern("[the] tail velocity smoothing of %cosmetique%")    // 2
                .addPattern("[the] tail velocity influence of %cosmetique%")    // 3
                .addPattern("[the] tail max deflection [angle] of %cosmetique%")// 4
                .addPattern("[the] tail depth deflection factor of %cosmetique%")// 5
                .addPattern("[the] tail undulation amplitude of %cosmetique%")  // 6
                .addPattern("[the] tail undulation frequency of %cosmetique%")  // 7
                .addPattern("[the] tail undulation propagation of %cosmetique%")// 8
                .addPattern("[the] tail random amplitude of %cosmetique%")      // 9
                .addPattern("[the] tail random frequency of %cosmetique%")      // 10
                .addPattern("[the] tail vertical velocity influence of %cosmetique%")// 11
                .addPattern("[the] tail max vertical deflection [angle] of %cosmetique%")// 12
                .build()
        );
    }
}
