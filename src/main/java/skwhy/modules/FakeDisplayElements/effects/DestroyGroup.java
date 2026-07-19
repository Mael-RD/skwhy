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

@Name("Destroy Display Group")
@Description("Destroys one or more fake display entity groups, removing them from all targeted players via packets.")
@Examples({
    "set {_group} to a new fake display group at player",
    "",
    "# Destroy a single group",
    "destroy display group {_group}",
    "",
    "# Destroy multiple groups at once",
    "destroy group {_groups::*}"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class DestroyGroup extends Effect {

    private Expression<DisplayGroupData> groupExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        groupExpr = (Expression<DisplayGroupData>) exprs[0];
        return true;
    }

    @Override
    protected void execute(Event event) {
        DisplayGroupData[] groups = groupExpr.getAll(event);
        if (groups == null) return;
        for (DisplayGroupData group : groups) {
            group.delete();
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "destroy display group " + groupExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(DestroyGroup.class)
                .addPattern("destroy [display] group %displaygroups%")
                .build()
        );
    }
}