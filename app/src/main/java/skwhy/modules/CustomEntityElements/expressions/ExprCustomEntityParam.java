package skwhy.modules.CustomEntityElements.expressions;

import java.util.Locale;

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
import skwhy.SkWhy;
import skwhy.customentity.CustomEntity;
import skwhy.customentity.CustomEntityManager;

@Name("[Custom Entity] Custom Entity Parameter")
@Description("A parameter of ONE custom entity, real or fake: its value is the override of the entity if it has one, else the value of its definition (nothing if the definition does not set it). " +
    "Setting it overrides the definition for this entity only, without creating a new definition: a real entity keeps the override in its NBT (\"BukkitValues\" → \"skwhy:custom_entity_overrides\", JSON), " +
    "so it survives a restart; deleting or resetting it gives back the value of the definition. " +
    "The names are the YAML paths of a definition: type, ai (type of the mob whose vanilla AI drives the entity), name, name_visible, scale, glowing, invisible, silent, on_fire, " +
    "hitbox.width, hitbox.height, skin.value, skin.signature, equipment.<slot> (main_hand, off_hand, head, chest, legs, feet, body) and metadata.<index> (written type:value, e.g. \"byte:1\"). " +
    "A number gives a number, true/false a boolean, anything else a text; an unknown name or a value of the wrong kind is refused with a warning in the console.")
@Examples({
    "set custom entity parameter \"scale\" of {_pet} to 2.5",
    "set custom entity parameter \"name\" of {_pet} to \"<red>Rex\"",
    "set custom entity parameter \"ai\" of {_pet} to \"zombie\"",
    "send custom entity parameter \"scale\" of {_pet} to player",
    "reset custom entity parameter \"scale\" of {_pet}"
})
@Since("1.6.0")
public class ExprCustomEntityParam extends SimpleExpression<Object> {

    private Expression<String> pathExpr;
    private Expression<CustomEntity> entityExpr;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(ExprCustomEntityParam.class, Object.class)
                .addPattern("custom entity (parameter|override) %string% of %customentity%")
                .build()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        pathExpr = (Expression<String>) exprs[0];
        entityExpr = (Expression<CustomEntity>) exprs[1];
        return true;
    }

    @Override
    protected @Nullable Object[] get(Event event) {
        CustomEntityManager manager = CustomEntityManager.get();
        String path = pathExpr.getSingle(event);
        CustomEntity entity = entityExpr.getSingle(event);
        if (manager == null || path == null || entity == null) return null;
        Object value = manager.param(entity, normalize(path));
        return value == null ? null : new Object[]{ value };
    }

    /** Un même paramètre peut s'écrire avec des majuscules ou des espaces ({@code "Name Visible"}). */
    private static String normalize(String path) {
        return path.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case SET -> new Class<?>[]{ Object.class };
            case DELETE, RESET -> new Class<?>[0];
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        CustomEntityManager manager = CustomEntityManager.get();
        String raw = pathExpr.getSingle(event);
        CustomEntity entity = entityExpr.getSingle(event);
        if (manager == null || raw == null || entity == null) return;
        String path = normalize(raw);
        if (mode != ChangeMode.SET) {
            manager.deleteParam(entity, path);
            return;
        }
        if (delta == null || delta.length == 0 || delta[0] == null) return;
        String problem = manager.setParam(entity, path, delta[0]);
        if (problem != null) SkWhy.getInstance().getLogger().warning("[CustomEntity] " + problem);
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<?> getReturnType() { return Object.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "custom entity parameter " + pathExpr.toString(event, debug) + " of " + entityExpr.toString(event, debug);
    }
}
