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
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.DisplayGroupData;

@Name("Group Location")
@Description("Gets or sets the current location of a fake display group. Setting the location will teleport the group to the new position.")
@Examples({
    "set {_group} to a new fake display group at player",
    "",
    "# Read the group's current location",
    "set {_loc} to group location of {_group}",
    "",
    "# Teleport the group to a new location",
    "set group location of {_group} to location of player"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class GroupLocation extends SimpleExpression<Location> {

    private Expression<DisplayGroupData> groupExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        groupExpr = (Expression<DisplayGroupData>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Location[] get(Event event) {
        DisplayGroupData group = groupExpr.getSingle(event);
        if (group == null) return null;
        return new Location[]{ group.getLocation() };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ Location.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || !(delta[0] instanceof Location loc)) return;
        DisplayGroupData group = groupExpr.getSingle(event);
        if (group == null) return;

        group.setLocation(loc);
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Location> getReturnType() { return Location.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "group location of " + groupExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(GroupLocation.class, Location.class)
                .addPattern("[the] group location of %displaygroup%")
                .build()
        );
    }
}