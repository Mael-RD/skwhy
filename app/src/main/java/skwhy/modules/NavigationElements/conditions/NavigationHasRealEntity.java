package skwhy.modules.NavigationElements.conditions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import org.skriptlang.skript.docs.Origin;

import skwhy.pathfinder.Navigation;

@Name("[Navigation] Has Real Entity")
@Description("Checks if one or more fakes navigations are using real entities.")
@Examples({
    "if {_navigation} has a real entity:",
    "\tsend \"This is a real entity!\"",
    "if all {_fakes::*} don't have real entities:",
    "\tsend \"None of the fake navigations are real entities.\""
})
@Since("1.2.0")
public class NavigationHasRealEntity extends Condition {

    // ── Enregistrement de la condition ─────────────────────────────────────────

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.CONDITION,
            SyntaxInfo.builder(NavigationHasRealEntity.class)
                .origin(Origin.of(addon))
                .addPattern("%navigations% (has|have) a real entity")
                .addPattern("%navigations% (has|have)(n't| not) a real entity")
                .build()
        );
    }

    // ── Variables ─────────────────────────────────────────────────────────────

    private Expression<Navigation> pathsExpr;

    // ── Initialisation ────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        this.pathsExpr = (Expression<Navigation>) exprs[0];
        setNegated(matchedPattern == 1);
        return true;
    }

    // ── Vérification ──────────────────────────────────────────────────────────

    @Override
    public boolean check(Event event) {
        Navigation[] paths = pathsExpr.getArray(event);
        if (paths.length == 0) return isNegated();

        for (Navigation path : paths) {
            if (path.isRealEntity() == isNegated()) return false;
        }
        return true;
    }

    // ── Affichage (Debug Skript) ──────────────────────────────────────────────

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return pathsExpr.toString(event, debug) + (isNegated() ? " hasn't a real entity" : " has a real entity");
    }
}