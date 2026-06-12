package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.doc.RequiredPlugins;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import org.joml.Quaternionf;
import skwhy.data.DisplayData;
import skwhy.data.Quat4;

@Name("Display Rotation")
@Description("Gets or sets the left or right rotation quaternion of a fake display entity. Both rotations are represented as Quat4 objects and affect how the display is oriented in the world.")
@Examples({
    "set {_group} to a new fake display group at player",
    "set {_display} to [a new fake item display]:",
    "    set item of display to dirt",
    "",
    "# Read the left rotation",
    "set {_leftRot} to left rotation of {_display}",
    "",
    "# Read the right rotation",
    "set {_rightRot} to right rotation of {_display}",
    "",
    "# Set a new left rotation",
    "set left rotation of {_display} to {_quaternion}",
    "",
    "# Set a new right rotation",
    "set right rotation of {_display} to {_quaternion}"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class DisplayRotation extends SimpleExpression<Quat4> {

    private int matchedPattern;
    private Expression<DisplayData> displayExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.matchedPattern = matchedPattern;
        displayExpr = (Expression<DisplayData>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Quat4[] get(Event event) {
        DisplayData d = displayExpr.getSingle(event);
        if (d == null) return null;

        Quaternionf rotation = switch (matchedPattern) {
            case 0 -> d.getLeftRotation().toQuaternionf();
            case 1 -> d.getRightRotation().toQuaternionf();
            default -> null;
        };

        // On utilise ton constructeur de Quat4 qui accepte un Quaternionf
        return rotation != null ? new Quat4[]{ new Quat4(rotation) } : null;
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        // Permet de faire : set left rotation of {_d} to new quat...
        if (mode == ChangeMode.SET) return new Class<?>[]{ Quat4.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || !(delta[0] instanceof Quat4 q)) return;
        
        DisplayData d = displayExpr.getSingle(event);
        if (d == null) return;

        // On applique la rotation en convertissant Quat4 en Quaternionf
        switch (matchedPattern) {
            case 0 -> d.setLeftRotation(q);
            case 1 -> d.setRightRotation(q);
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Quat4> getReturnType() { return Quat4.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        String type = (matchedPattern == 0) ? "left rotation" : "right rotation";
        return type + " of " + displayExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(DisplayRotation.class, Quat4.class)
                .addPattern("[the] left rotation of %displaydata%")    // 0
                .addPattern("[the] right rotation of %displaydata%")   // 1
                .build()
        );
    }
}