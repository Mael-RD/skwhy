package skwhy.modules.FakePathFindingElements.conditions;

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

import skwhy.data.FakePathFinding;
import skwhy.modules.VoiceModule;
import skwhy.voice.VoiceListener;

@Name("Is Real Entity")
@Description("Checks if one or more fake pathfindings are real entities.")
@Examples({
    "if {_fakePath} is a real entity:",
    "\tsend \"This is a real entity!\"",
    "if all {_fakes::*} aren't real entities:",
    "\tsend \"None of the fake pathfindings are real entities.\""
})
@Since("1.2.0")
public class RealEntity extends Condition {

    // ── Enregistrement de la condition ─────────────────────────────────────────

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.CONDITION,
            SyntaxInfo.builder(RealEntity.class)
                .origin(Origin.of(addon))
                .addPattern("%fakepathfindings% (is|are) a real entity")
                .addPattern("%fakepathfindings% (isn't|aren't) a real entity")
                .build()
        );
    }

    // ── Variables ─────────────────────────────────────────────────────────────

    private Expression<FakePathFinding> pathsExpr;

    // ── Initialisation ────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        this.pathsExpr = (Expression<FakePathFinding>) exprs[0];
        setNegated(matchedPattern == 1);
        return true;
    }

    // ── Vérification ──────────────────────────────────────────────────────────

    @Override
    public boolean check(Event event) {
        FakePathFinding[] paths = pathsExpr.getArray(event);
        if (paths.length == 0) return isNegated();

        VoiceListener listener = VoiceModule.getVoiceListener();
        if (listener == null) return isNegated();

        for (FakePathFinding path : paths) {
            if (path.isRealEntity() == isNegated()) return false;
        }
        return true;
    }

    // ── Affichage (Debug Skript) ──────────────────────────────────────────────

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return pathsExpr.toString(event, debug) + (isNegated() ? " isn't a real entity" : " is a real entity");
    }
}