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
import skwhy.data.DisplayGroupData;

@Name("Set Group Direction")
@Description("Orients a display group toward a specific direction by setting its yaw and pitch values directly.")
@Examples({
    "set {_group} to a new fake display group at player",
    "",
    "# Set direction using explicit yaw and pitch angles",
    "set direction of {_group} to 90 and 45",
    "",
    "# Using the 'yaw and pitch' alias",
    "set yaw and pitch of {_group} to 180 and 0"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class SetGroupDirection extends Effect {

    private Expression<DisplayGroupData> groupExpr;
    private Expression<Number> yawExpr;
    private Expression<Number> pitchExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        groupExpr = (Expression<DisplayGroupData>) exprs[0];
        yawExpr   = (Expression<Number>) exprs[1];
        pitchExpr = (Expression<Number>) exprs[2];
        return true;
    }

    @Override
    protected void execute(Event event) {
        DisplayGroupData group = groupExpr.getSingle(event);
        Number yaw             = yawExpr.getSingle(event);
        Number pitch           = pitchExpr.getSingle(event);

        if (group == null || yaw == null || pitch == null) return;

        group.setYawPitch(yaw.floatValue(), pitch.floatValue());
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "set direction of " + groupExpr.toString(event, debug)
            + " to yaw " + yawExpr.toString(event, debug)
            + " and pitch " + pitchExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(SetGroupDirection.class)
                .addPattern("set (direction|yaw and pitch) of %displaygroup% to %number% [and] %number%")
                .build()
        );
    }
}