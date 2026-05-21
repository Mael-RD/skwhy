package skwhy.modules.FakeDisplayElements.effects;

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
import skwhy.data.CosmetiqueData; // N'oublie pas l'import

public class SendUpdate extends Effect {

    // On utilise Object pour accepter les deux types
    private Expression<Object> targetExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        targetExpr = (Expression<Object>) exprs[0];
        return true;
    }

    @Override
    protected void execute(Event event) {
        Object target = targetExpr.getSingle(event);
        if (target == null) return;

        // On cast directement et on appelle la méthode correspondante
        if (target instanceof DisplayGroupData group) {
            group.updateMetadata();
        } else if (target instanceof CosmetiqueData cosme) {
            cosme.update(); 
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "send update " + targetExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(SendUpdate.class)
                .addPattern("send update %displaygroup/cosmetique%")
                .build()
        );
    }
}