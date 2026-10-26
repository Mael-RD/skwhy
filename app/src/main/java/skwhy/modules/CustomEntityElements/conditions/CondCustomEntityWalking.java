package skwhy.modules.CustomEntityElements.conditions;

import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import skwhy.customentity.CustomEntity;
import skwhy.customentity.CustomEntityManager;

@Name("[Custom Entity] Custom Entity Is Walking")
@Description("Checks whether the AI mob of a custom entity is following a path (started with 'make custom entity walk to' or by its own goals). " +
    "False for an entity without AI.")
@Examples({
    "if custom entity {_pet} is walking:",
    "\tsend \"Il marche\" to player",
    "if custom entity {_pet} isn't walking:",
    "\tmake custom entity {_pet} walk to location of player"
})
@Since("1.6.0")
public class CondCustomEntityWalking extends Condition {

    private Expression<CustomEntity> entities;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.CONDITION,
            SyntaxInfo.builder(CondCustomEntityWalking.class)
                .addPattern("custom entit(y|ies) %customentities% (is|are) walking")
                .addPattern("custom entit(y|ies) %customentities% (isn't|is not|aren't|are not) walking")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        entities = (Expression<CustomEntity>) exprs[0];
        setNegated(matchedPattern == 1);
        return true;
    }

    @Override
    public boolean check(Event event) {
        CustomEntityManager manager = CustomEntityManager.get();
        if (manager == null) return isNegated();
        return entities.check(event, manager::isWalking, isNegated());
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "custom entities " + entities.toString(event, debug) + (isNegated() ? " are not walking" : " are walking");
    }
}
