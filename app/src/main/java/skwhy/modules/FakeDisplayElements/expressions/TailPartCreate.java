package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.data.DisplayGroupData;
import skwhy.data.Tail;
import skwhy.data.Tail.TailNode;
import skwhy.data.Vec3;
import skwhy.data.Quat4;

import org.bukkit.util.Vector;
import org.joml.Quaternionf;

public class TailPartCreate extends SimpleExpression<TailNode> {

    // Éléments requis de base
    private Expression<DisplayGroupData> displayExpr;
    private Expression<Vector> transExpr;
    private Expression<Quaternionf> rotExpr;

    // Chaînage (Optionnel : si spécifié -> Segment, si absent -> Racine)
    private Expression<TailNode> parentExpr;

    // Paramètres de configuration (Optionnels)
    private Expression<Number> rigidityExpr;
    private Expression<Number> dampingExpr;
    private Expression<Number> velSmoothingExpr;
    private Expression<Number> velInfluenceExpr;
    private Expression<Number> maxDeflectionExpr;
    private Expression<Number> depthDeflectionExpr;
    private Expression<Number> undulationAmpExpr;
    private Expression<Number> undulationFreqExpr;
    private Expression<Number> undulationPropExpr;
    private Expression<Number> randomAmpExpr;
    private Expression<Number> randomFreqExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        // En fonction du pattern, les index peuvent changer. L'utilisation d'un système à index fixe
        // basé sur l'ordre du pattern est recommandée.
        
        this.displayExpr = (Expression<DisplayGroupData>) exprs[0];
        this.transExpr = (Expression<Vector>) exprs[1];
        this.rotExpr = (Expression<Quaternionf>) exprs[2];
        
        // Extraction des expressions du pattern
        this.parentExpr = (Expression<TailNode>) exprs[3];
        this.rigidityExpr = (Expression<Number>) exprs[4];
        this.dampingExpr = (Expression<Number>) exprs[5];
        this.velSmoothingExpr = (Expression<Number>) exprs[6];
        this.velInfluenceExpr = (Expression<Number>) exprs[7];
        this.maxDeflectionExpr = (Expression<Number>) exprs[8];
        this.depthDeflectionExpr = (Expression<Number>) exprs[9];
        this.undulationAmpExpr = (Expression<Number>) exprs[10];
        this.undulationFreqExpr = (Expression<Number>) exprs[11];
        this.undulationPropExpr = (Expression<Number>) exprs[12];
        this.randomAmpExpr = (Expression<Number>) exprs[13];
        this.randomFreqExpr = (Expression<Number>) exprs[14];

        return true;
    }

    @Override
    @Nullable
    protected TailNode[] get(Event event) {
        DisplayGroupData display = displayExpr.getSingle(event);
        Vector trans = transExpr.getSingle(event);
        Quaternionf rot = rotExpr.getSingle(event);

        if (display == null || trans == null || rot == null) return null;

        TailNode parentNode = parentExpr != null ? parentExpr.getSingle(event) : null;
        TailNode node;
        Tail tailInstance;

        // 1. Détermination s'il s'agit d'une racine ou d'un enfant
        if (parentNode == null) {
            // Création d'une nouvelle structure de Queue (Racine)
            tailInstance = new Tail(display, new Vec3(trans), new Quat4(rot));
            node = tailInstance.getRoot();
        } else {
            // Ajout à une queue existante (Pour retrouver l'instance de Tail depuis un TailNode,
            // il faut s'assurer que TailNode puisse accéder à sa classe parente Tail, ce qui est natif en Java car c'est une inner class non-statique)
            tailInstance = parentNode.display.hashCode() != 0 ? parentNode.getTailFromNode() : null; 
            
            // Note de sécurité : Comme TailNode est une classe interne (non-statique) de Tail, 
            // on peut récupérer l'instance de Tail directement via : parentNode.Tail.this si le scope le permet, 
            // ou en passant par une astuce de conception. Ici, on va utiliser l'instance parent ou lever une sécurité.
            try {
                // En Java, une inner class possède une référence cachée vers sa classe externe nommée "this$0"
                java.lang.reflect.Field field = parentNode.getClass().getDeclaredField("this$0");
                field.setAccessible(true);
                tailInstance = (Tail) field.get(parentNode);
            } catch (Exception e) {
                return null; // Échec de récupération de l'instance principale
            }

            node = tailInstance.addSegment(parentNode, display, new Vec3(trans), new Quat4(rot));
        }

        // 2. Application des configurations fluides de Tail.java (uniquement si spécifiées)
        if (rigidityExpr != null) tailInstance.setRigidity(rigidityExpr.getSingle(event).floatValue());
        if (dampingExpr != null) tailInstance.setDamping(dampingExpr.getSingle(event).floatValue());
        if (velSmoothingExpr != null) tailInstance.setVelocitySmoothing(velSmoothingExpr.getSingle(event).floatValue());
        if (velInfluenceExpr != null) tailInstance.setVelocityInfluence(velInfluenceExpr.getSingle(event).floatValue());
        if (maxDeflectionExpr != null) tailInstance.setMaxDeflectionAngle(maxDeflectionExpr.getSingle(event).floatValue());
        if (depthDeflectionExpr != null) tailInstance.setDepthDeflectionFactor(depthDeflectionExpr.getSingle(event).floatValue());
        if (undulationAmpExpr != null) tailInstance.setUndulationAmplitude(undulationAmpExpr.getSingle(event).floatValue());
        if (undulationFreqExpr != null) tailInstance.setUndulationFrequency(undulationFreqExpr.getSingle(event).floatValue());
        if (undulationPropExpr != null) tailInstance.setUndulationPropagation(undulationPropExpr.getSingle(event).floatValue());
        if (randomAmpExpr != null) tailInstance.setRandomAmplitude(randomAmpExpr.getSingle(event).floatValue());
        if (randomFreqExpr != null) tailInstance.setRandomFrequency(randomFreqExpr.getSingle(event).floatValue());

        return new TailNode[]{ node };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends TailNode> getReturnType() { return TailNode.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "new tail part from " + displayExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(TailPartCreate.class, TailNode.class)
                .addPattern("[a] [new] tail[ ]part from %displaygroup% with offset %vector% and rot %quaternion% " +
                           "[[,] [connected] to parent %-tailpart%] " +
                           "[[,] [with] rigidity %-number%] " +
                           "[[,] [with] damping %-number%] " +
                           "[[,] [with] velocity smoothing %-number%] " +
                           "[[,] [with] velocity influence %-number%] " +
                           "[[,] [with] max deflection %-number%] " +
                           "[[,] [with] depth deflection %-number%] " +
                           "[[,] [with] undulation amp[litude] %-number%] " +
                           "[[,] [with] undulation freq[uency] %-number%] " +
                           "[[,] [with] undulation prop[agation] %-number%] " +
                           "[[,] [with] random amp[litude] %-number%] " +
                           "[[,] [with] random freq[uency] %-number%]")
                .build()
        );
    }
}