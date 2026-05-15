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

public class SendUpdate extends Effect {

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
        DisplayGroupData group = groupExpr.getSingle(event);
        if (group == null) return;
        group.updateMetadata();
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "send update " + groupExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(SendUpdate.class)
                .addPattern("send update %displaygroup%")
                .build()
        );
    }
}