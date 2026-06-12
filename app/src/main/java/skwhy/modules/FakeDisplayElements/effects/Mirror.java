package skwhy.modules.FakeDisplayElements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.doc.RequiredPlugins;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.data.DisplayData;
import skwhy.data.DisplayGroupData;

@Name("Mirror Transformation")
@Description("Applies a mirror (axis flip) transformation to one or more display entities or display groups. Axes can be combined freely: X, Y, Z, XY, XZ, YZ, or XYZ.")
@Examples({
    "set {_group} to a new fake display group at player",
    "set {_display} to [a new fake item display]:",
    "    set item of display to dirt",
    "",
    "# Mirror a group on a single axis",
    "mirror {_group} on axis x",
    "",
    "# Mirror a display entity on two axes",
    "mirror {_display} on axes xz",
    "",
    "# Mirror multiple targets on all axes",
    "mirror {_group} on axes xyz"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class Mirror extends Effect {

    private Expression<?> targetExpr;
    private boolean mirrorX;
    private boolean mirrorY;
    private boolean mirrorZ;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, ParseResult pr) {
        
        this.targetExpr = exprs[0];

        // On décode les axes choisis grâce aux bits binaires définis dans le pattern (1, 2, 4)
        this.mirrorX = (pr.mark & 1) != 0;
        this.mirrorY = (pr.mark & 2) != 0;
        this.mirrorZ = (pr.mark & 4) != 0;

        // Sécurité : Si aucun axe n'est spécifié (ex: juste "mirror {_g}"), on ne valide pas la syntaxe
        if (!mirrorX && !mirrorY && !mirrorZ) {
            return false;
        }

        return true;
    }

    @Override
    protected void execute(Event event) {
        // Récupération de tous les objets (gère aussi le cas où l'utilisateur passe une liste)
        Object[] targets = targetExpr.getAll(event);
        if (targets == null) return;

        for (Object target : targets) {
            if (target instanceof DisplayGroupData group) {
                group.mirror(mirrorX, mirrorY, mirrorZ);
            } else if (target instanceof DisplayData display) {
                display.mirror(mirrorX, mirrorY, mirrorZ);
            }
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        StringBuilder axes = new StringBuilder();
        if (mirrorX) axes.append("X ");
        if (mirrorY) axes.append("Y ");
        if (mirrorZ) axes.append("Z ");
        return "mirror " + targetExpr.toString(event, debug) + " on axes " + axes.toString().trim();
    }
    
    public static void register(SkriptAddon addon) {
            addon.syntaxRegistry().register(
                SyntaxRegistry.EFFECT,
                SyntaxInfo.builder(Mirror.class)
                    // Toutes les permutations possibles condensées en un seul bloc logique.
                    // L'utilisateur devra attacher les lettres (ex: "xy", "yzx", etc.)
                    .addPattern("mirror %displaydatas/displaygroups% [on [axis|axes]] (1:x|2:y|4:z|3:xy|3:yx|5:xz|5:zx|6:yz|6:zy|7:xyz|7:xzy|7:yxz|7:yzx|7:zxy|7:zyx)")
                    .build()
            );
        }
}