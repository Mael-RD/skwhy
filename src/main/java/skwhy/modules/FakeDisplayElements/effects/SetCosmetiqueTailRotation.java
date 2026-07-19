package skwhy.modules.FakeDisplayElements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.doc.RequiredPlugins;

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
import org.joml.Quaternionf;

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
@Name("Set Cosmetic Tail Rotation")
@Description("Sets the rest rotations for all segments of a cosmetic's tail by providing a list of Quaternionf values. Each quaternion corresponds to one tail segment, iterated in depth-first order (DFS).")
@Examples({
    "set {_cosmetique} to a new cosmetique for player",
    "",
    "# Apply a list of quaternion rotations to the tail segments",
    "set tail rotation of {_cosmetique} to {_quaternions::*}"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class SetCosmetiqueTailRotation extends Effect {

    private Expression<CosmetiqueData> cosmetiqueExpr;
    private Expression<Quaternionf> quaternionsExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.cosmetiqueExpr = (Expression<CosmetiqueData>) exprs[0];
        this.quaternionsExpr = (Expression<Quaternionf>) exprs[1];
        return true;
    }

    @Override
    protected void execute(Event event) {
        CosmetiqueData cosmetique = cosmetiqueExpr.getSingle(event);
        if (cosmetique == null) return;

        // Récupérer tous les quaternions de la liste
        Quaternionf[] quaternions = quaternionsExpr.getAll(event);
        if (quaternions == null || quaternions.length == 0) return;

        // Convertir le tableau de Quaternionf en List<Quaternionf>
        List<Quaternionf> rotations = new ArrayList<>();
        for (Quaternionf q : quaternions) {
            rotations.add(q);
        }

        // Appliquer les rotations à la queue
        cosmetique.setTailRestRotation(rotations);
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "set tail rotation of " + cosmetiqueExpr.toString(event, debug)
            + " to " + quaternionsExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(SetCosmetiqueTailRotation.class)
                .addPattern("set tail rotation of %cosmetique% to %quaternions%")
                .build()
        );
    }
}
