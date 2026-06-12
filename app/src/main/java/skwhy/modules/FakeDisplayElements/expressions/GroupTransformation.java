package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.doc.RequiredPlugins;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.DisplayGroupData;
import skwhy.data.Vec3;
import skwhy.data.Quat4;

@Name("Group Transformation")
@Description("Gets or sets the center, translation, rotation, or scale of a display group. Center and translation return a Vector; rotation returns a Quaternionf; scale returns a Number.")
@Examples({
    "set {_group} to a new fake display group at player",
    "",
    "# Get and set the group center (pivot point)",
    "set {_center} to group center of {_group}",
    "set group center of {_group} to vector(0, 1, 0)",
    "",
    "# Get and set the translation offset",
    "set {_translation} to group translation of {_group}",
    "set group translation of {_group} to vector(0, 0.5, 0)",
    "",
    "# Get and set the rotation quaternion",
    "set {_rotation} to group rotation of {_group}",
    "set group rotation of {_group} to {_quaternion}",
    "",
    "# Get and set the uniform scale factor",
    "set {_scale} to group scale of {_group}",
    "set group scale of {_group} to 2"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class GroupTransformation extends SimpleExpression<Object> {

    // 0 = center      → Vector
    // 1 = translation → Vector
    // 2 = rotation    → Quaternionf
    // 3 = scale       → Number (float)

    private int matchedPattern;
    private Expression<DisplayGroupData> groupExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.matchedPattern = matchedPattern;
        this.groupExpr      = (Expression<DisplayGroupData>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Object[] get(Event event) {
        DisplayGroupData group = groupExpr.getSingle(event);
        if (group == null) return null;

        return switch (matchedPattern) {
            case 0 -> {
                Vector c = group.getCenter().toVector();
                yield c != null ? new Object[]{ c } : null;
            }
            case 1 -> {
                Vector t = group.getTranslation().toVector();
                yield t != null ? new Object[]{ t } : null;
            }
            case 2 -> {
                Quaternionf r = group.getRotation().toQuaternionf();
                yield r != null ? new Object[]{ r } : null;
            }
            case 3 -> new Object[]{ group.getScale() };
            default -> null;
        };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode != ChangeMode.SET) return null;
        return switch (matchedPattern) {
            case 0, 1 -> new Class<?>[]{ Vector.class };
            case 2    -> new Class<?>[]{ Quaternionf.class };
            case 3    -> new Class<?>[]{ Number.class };
            default   -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || delta[0] == null) return;
        DisplayGroupData group = groupExpr.getSingle(event);
        if (group == null) return;

        switch (matchedPattern) {
            case 0 -> { if (delta[0] instanceof Vector v)      group.setCenter(new Vec3(v)); }
            case 1 -> { if (delta[0] instanceof Vector v)      group.setTranslation(new Vec3(v)); }
            case 2 -> { if (delta[0] instanceof Quaternionf q) group.setRotation(new Quat4(q)); }
            case 3 -> { if (delta[0] instanceof Number n)      group.setScale(n.floatValue()); }
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<?> getReturnType() {
        return switch (matchedPattern) {
            case 0, 1 -> Vector.class;
            case 2    -> Quaternionf.class;
            case 3    -> Number.class;
            default   -> Object.class;
        };
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        String prop = switch (matchedPattern) {
            case 0 -> "center";
            case 1 -> "translation";
            case 2 -> "rotation";
            case 3 -> "scale";
            default -> "unknown";
        };
        return prop + " of " + groupExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(GroupTransformation.class, Object.class)
                .addPattern("[the] group center of %displaygroup%")      // 0
                .addPattern("[the] group translation of %displaygroup%") // 1
                .addPattern("[the] group rotation of %displaygroup%")    // 2
                .addPattern("[the] group scale of %displaygroup%")       // 3
                .build()
        );
    }
}