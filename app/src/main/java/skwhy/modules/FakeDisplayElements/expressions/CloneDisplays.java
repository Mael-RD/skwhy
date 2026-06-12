package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.doc.RequiredPlugins;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.data.DisplayData;
import skwhy.data.DisplayGroupData;

import java.util.ArrayList;
import java.util.List;

@Name("Clone Displays")
@Description("Clones one or more display datas or display groups, optionally applying a mirror transformation on one or more axes during cloning. Without an axis, produces an exact copy.")
@Examples({
    "set {_group} to a new fake display group at player",
    "set {_display} to [a new fake item display]:",
    "    set item of display to dirt",
    "",
    "# Clone a display without mirroring",
    "set {_clone} to fake display clone of {_display}",
    "",
    "# Clone a group without mirroring",
    "set {_groupClone} to display clone of {_group}",
    "",
    "# Clone and mirror on X axis",
    "set {_mirrorX} to fake display clone x of {_display}",
    "",
    "# Clone and mirror on Y axis",
    "set {_mirrorY} to display clone y of {_group}",
    "",
    "# Clone and mirror on XY axes",
    "set {_mirrorXY} to fake display clone xy of {_display}",
    "",
    "# Clone and mirror on all axes",
    "set {_mirrorXYZ} to display clone xyz of {_group}"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class CloneDisplays extends SimpleExpression<Object> {

    private Expression<?> targets;
    private boolean mirrorX;
    private boolean mirrorY;
    private boolean mirrorZ;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        this.targets = exprs[0];

        // Le pr.mark contient la somme des valeurs binaires définies dans le pattern choisi.
        // 1 = X, 2 = Y, 4 = Z
        this.mirrorX = (pr.mark & 1) != 0;
        this.mirrorY = (pr.mark & 2) != 0;
        this.mirrorZ = (pr.mark & 4) != 0;

        return true;
    }

    @Override
    @Nullable
    protected Object[] get(Event event) {
        Object[] elements = targets.getAll(event);
        if (elements == null) return null;

        List<Object> clonedElements = new ArrayList<>();
        for (Object obj : elements) {
            if (obj instanceof DisplayGroupData group) {
                clonedElements.add(group.clone(mirrorX, mirrorY, mirrorZ));
            } else if (obj instanceof DisplayData display) {
                clonedElements.add(display.clone(mirrorX, mirrorY, mirrorZ));
            }
        }
        return clonedElements.toArray();
    }

    @Override
    public boolean isSingle() {
        return targets.isSingle();
    }

    @Override
    public Class<? extends Object> getReturnType() {
        return Object.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "clone of " + targets.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        // Définition de la liste des permutations d'axes.
        // On utilise la syntaxe (1:x|2:y|4:z|3:xy|...) pour que Skript assigne 
        // automatiquement la valeur binaire au pr.mark.
        
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(CloneDisplays.class, Object.class)
                .addPattern("[fake] [display[s]] clone of %displaydatas/displaygroups%")
                .addPattern("[fake] [display[s]] clone (1:x|2:y|4:z|3:xy|3:yx|5:xz|5:zx|6:yz|6:zy|7:xyz|7:xzy|7:yxz|7:yzx|7:zxy|7:zyx) of %displaydatas/displaygroups%")
                .build()
        );
    }
}