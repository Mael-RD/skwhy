package skwhy.modules.RandomStuff.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import io.github.retrooper.packetevents.util.SpigotReflectionUtil;

/**
 * Expression pour générer un nouvel ID d'entité unique.
 * Utilise SpigotReflectionUtil.generateEntityId() pour créer un ID valide.
 */
public class GenerateId extends SimpleExpression<Integer> {

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        return true;
    }

    @Override
    protected @Nullable Integer[] get(Event event) {
        int entityId = SpigotReflectionUtil.generateEntityId();
        return new Integer[]{ entityId };
    }

    @Override
    public boolean isSingle() { 
        return true; 
    }

    @Override
    public Class<? extends Integer> getReturnType() { 
        return Integer.class; 
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "generate entity id";
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(GenerateId.class, Integer.class)
                .addPattern("[generate|a|an] [new] entity id")
                .build()
        );
    }
}
