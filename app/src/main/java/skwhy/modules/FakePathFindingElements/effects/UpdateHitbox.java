package skwhy.modules.FakePathFindingElements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.FakePathFinding;

@Name("Update Fake Pathfinding Hitbox")
@Description("Refreshes the hitbox of one or more fake pathfinding objects from their attached real entity's current dimensions. " +
    "Only has an effect on pathfinding instances created from a real entity — hitboxes of numeric ID-based instances are ignored.")
@Examples({
    "set {_fake} to a new fake pathfinding with entity target entity hitbox vector(0.6, 1.8, 0.6) location location of player type \"WALK\"",
    "",
    "# Refresh the hitbox from the entity's current size",
    "update hitbox of {_fake}",
    "",
    "# Works on multiple instances at once",
    "update hitbox of {_fakes::*}"
})
@Since("1.2.0")
public class UpdateHitbox extends Effect {

    private Expression<FakePathFinding> fakesExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        fakesExpr = (Expression<FakePathFinding>) exprs[0];
        return true;
    }

    @Override
    protected void execute(Event event) {
        FakePathFinding[] fakes = fakesExpr.getAll(event);
        if (fakes == null) return;
        for (FakePathFinding fake : fakes) {
            fake.updateHitbox();
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "update hitbox of " + fakesExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(UpdateHitbox.class)
                .addPattern("update hitbox of %fakepathfindings%")
                .build()
        );
    }
}