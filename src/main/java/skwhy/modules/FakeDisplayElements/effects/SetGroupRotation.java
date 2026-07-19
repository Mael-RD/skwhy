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
import org.joml.Quaternionf;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.DisplayGroupData;
import skwhy.data.Quat4;

@Name("Set Group Rotation")
@Description("Sets the full 3D rotation of a display group using Euler angles (X, Y, Z in degrees). Internally converts to a quaternion using JOML's rotationXYZ method.")
@Examples({
    "set {_group} to a new fake display group at player",
    "",
    "# Set rotation with pitch, yaw and roll in degrees",
    "set group rotation of {_group} to 90, 45, 0"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class SetGroupRotation extends Effect {

    private Expression<DisplayGroupData> groupExpr;
    private Expression<Number> xExpr, yExpr, zExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        groupExpr = (Expression<DisplayGroupData>) exprs[0];
        xExpr     = (Expression<Number>) exprs[1];
        yExpr     = (Expression<Number>) exprs[2];
        zExpr     = (Expression<Number>) exprs[3];
        return true;
    }

    @Override
    protected void execute(Event event) {
        DisplayGroupData group = groupExpr.getSingle(event);
        Number x = xExpr.getSingle(event);
        Number y = yExpr.getSingle(event);
        Number z = zExpr.getSingle(event);

        if (group == null || x == null || y == null || z == null) return;

        // Conversion degrés → radians
        float rx = (float) Math.toRadians(x.doubleValue());
        float ry = (float) Math.toRadians(y.doubleValue());
        float rz = (float) Math.toRadians(z.doubleValue());

        // Conversion angles d'Euler XYZ → quaternion via JOML
        Quaternionf quaternion = new Quaternionf().rotationXYZ(rx, ry, rz);

        group.setRotation(new Quat4(quaternion));
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "set group rotation of " + groupExpr.toString(event, debug)
            + " to " + xExpr.toString(event, debug)
            + ", "   + yExpr.toString(event, debug)
            + ", "   + zExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(SetGroupRotation.class)
                .addPattern("set group rotation of %displaygroup% to %number%[,] %number%[,] %number%")
                .build()
        );
    }
}