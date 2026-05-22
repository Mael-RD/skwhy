package skwhy.modules.FakeDisplayElements.effects;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.CosmetiqueData;

import java.util.ArrayList;
import java.util.List;

/**
 * Effet permettant de définir les rotations repos de tous les segments de la queue
 * d'une cosmétique à partir d'une liste de nombres.
 *
 * <p>La liste doit contenir 4 nombres (x, y, z, w) par segment de queue,
 * dans l'ordre d'un parcours profondeur d'abord (DFS).
 *
 * <p>Usage : set tail rotation of %cosmetique% to %numbers%
 * Exemple : set tail rotation of my_cosmetique to {0.1, 0.2, 0.3, 0.9, 0.0, 0.0, 0.5, 0.87}
 */
public class SetCosmetiqueTailRotation extends Effect {

    private Expression<CosmetiqueData> cosmetiqueExpr;
    private Expression<Number> numbersExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.cosmetiqueExpr = (Expression<CosmetiqueData>) exprs[0];
        this.numbersExpr = (Expression<Number>) exprs[1];
        return true;
    }

    @Override
    protected void execute(Event event) {
        CosmetiqueData cosmetique = cosmetiqueExpr.getSingle(event);
        if (cosmetique == null) return;

        // Récupérer tous les nombres de la liste
        Number[] numbers = numbersExpr.getAll(event);
        if (numbers == null || numbers.length == 0) return;

        // Convertir le tableau de Number en List<Float>
        List<Float> rotations = new ArrayList<>();
        for (Number n : numbers) {
            rotations.add(n.floatValue());
        }

        // Appliquer les rotations à la queue
        cosmetique.setTailRestRotation(rotations);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "set tail rotation of " + cosmetiqueExpr.toString(event, debug)
            + " to " + numbersExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(SetCosmetiqueTailRotation.class)
                .addPattern("set tail rotation of %cosmetique% to %numbers%")
                .build()
        );
    }
}
