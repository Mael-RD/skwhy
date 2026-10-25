package skwhy.modules.CustomEntityElements.expressions;

import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import skwhy.customentity.CustomEntity;
import skwhy.customentity.CustomEntityManager;

@Name("[Custom Entity] Custom Entity Id")
@Description("The id of the definition of a custom entity. For an interaction entity of the server it is stored in its NBT " +
    "(\"BukkitValues\" → \"skwhy:custom_entity\", plus \"skwhy:custom_entity_overrides\" if the entity overrides some parameters): setting it makes the interaction look like that definition for the players around, " +
    "and its hitbox takes the size of the definition; deleting it gives back an ordinary interaction. " +
    "Setting the id of a custom entity changes its definition; deleting it destroys the custom entity. " +
    "Nothing happens if the id is not defined or the entity is not an interaction.")
@Examples({
    "spawn an interaction at location of player:",
    "\tset custom entity id of event-entity to \"example_wolf\"",
    "send custom entity id of target entity to player",
    "delete custom entity id of target entity"
})
@Since("1.6.0")
public class ExprCustomEntityId extends SimpleExpression<String> {

    private Expression<?> source;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprCustomEntityId.class, String.class)
                .addPattern("custom entity id of %entity/customentity%")
                .build()
        );
    }

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        source = exprs[0];
        return true;
    }

    @Override
    protected @Nullable String[] get(Event event) {
        CustomEntityManager manager = CustomEntityManager.get();
        Object value = source.getSingle(event);
        if (manager == null || value == null) return null;
        String id = value instanceof CustomEntity entity ? entity.definitionId() : manager.idOf((Entity) value);
        return id == null ? null : new String[]{ id };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case SET -> new Class<?>[]{ String.class };
            case DELETE, RESET -> new Class<?>[0];
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        CustomEntityManager manager = CustomEntityManager.get();
        Object value = source.getSingle(event);
        if (manager == null || value == null) return;
        String id = mode == ChangeMode.SET && delta != null && delta.length > 0 ? String.valueOf(delta[0]) : null;
        if (value instanceof CustomEntity entity) {
            if (id != null) manager.setId(entity, id);
            else manager.destroy(entity);
        } else {
            manager.setId((Entity) value, id);
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends String> getReturnType() { return String.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "custom entity id of " + source.toString(event, debug);
    }
}
