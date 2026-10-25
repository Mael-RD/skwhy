package skwhy.modules.CustomEntityElements.expressions;

import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import skwhy.customentity.CustomEntityManager;

@Name("[Custom Entity] Custom Entity Ids")
@Description("The ids of all the registered custom entity definitions (the entries of the files of the custom-entities folder), sorted.")
@Examples({
    "loop custom entity ids:",
    "\tbroadcast loop-value"
})
@Since("1.6.0")
public class ExprCustomEntityIds extends SimpleExpression<String> {

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprCustomEntityIds.class, String.class)
                .addPattern("[all] custom entity ids")
                .build()
        );
    }

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        return true;
    }

    @Override
    protected @Nullable String[] get(Event event) {
        CustomEntityManager manager = CustomEntityManager.get();
        return manager == null ? new String[0] : manager.registry().ids().toArray(new String[0]);
    }

    @Override
    public boolean isSingle() { return false; }

    @Override
    public Class<? extends String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "custom entity ids";
    }
}
