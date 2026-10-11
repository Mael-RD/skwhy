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
@Description("Gets or sets the left or right rotation quaternion of fake display entities. Both rotations are represented as Quat4 objects and affect how the display is oriented in the world. Targets can be one or more displays or a display group.")
@Examples({
    "set {_group} to a new fake display group at player",
    "set {_display} to a new fake item display:",
    "    item: dirt",
    "",
    "# Read the left rotation",
    "set {_leftRot} to fake display left rotation of {_display}",
    "",
    "# Read the right rotation",
    "set {_rightRot} to fake display right rotation of {_display}",
    "",
    "# Set a new left rotation",
    "set fake display left rotation of {_display} to {_quaternion}",
    "",
    "# Set a new right rotation",
    "set fake display right rotation of {_display} to {_quaternion}"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class DisplayRotation extends SimpleExpression<Quaternionf> {

    private int matchedPattern;
    private boolean pluralTargets;
    private Expression<?> targets;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.pluralTargets = matchedPattern >= 2;
        this.matchedPattern = matchedPattern % 2;
        targets = exprs[0];
        return true;
    }

    @Override
    protected @Nullable Quaternionf[] get(Event event) {
        if (pluralTargets) return null;
        Object value = targets.getSingle(event);
        if (!(value instanceof DisplayData display)) return null;
        Quaternionf rotation = switch (matchedPattern) {
            case 0 -> display.getLeftRotation().toQuaternionf();
            case 1 -> display.getRightRotation().toQuaternionf();
            default -> null;
        };
        return rotation == null ? null : new Quaternionf[]{ rotation };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        // set fake display left/right rotation of {_d} to quaternion(...)
        if (mode == ChangeMode.SET) return new Class<?>[]{ Quaternionf.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || !(delta[0] instanceof Quaternionf quaternion)) return;
        Quat4 q = new Quat4(quaternion);
        
        for (DisplayData display : DisplayExpressionTargets.resolveForChange(targets, event, pluralTargets)) {
            switch (matchedPattern) {
                case 0 -> display.setLeftRotation(q);
                case 1 -> display.setRightRotation(q);
            }
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Quaternionf> getReturnType() { return Quaternionf.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        String type = (matchedPattern == 0) ? "left rotation" : "right rotation";
        return type + " of " + targets.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(DisplayRotation.class, Quaternionf.class)
                .addPattern("[the] fake display left rotation of %displaydata/displaygroup%")    // 0
                .addPattern("[the] fake display right rotation of %displaydata/displaygroup%")   // 1
                .addPattern("[the] fake display left rotation of %displaydatas/displaygroups%")    // 2
                .addPattern("[the] fake display right rotation of %displaydatas/displaygroups%")   // 3
                .build()
        );
    }
}