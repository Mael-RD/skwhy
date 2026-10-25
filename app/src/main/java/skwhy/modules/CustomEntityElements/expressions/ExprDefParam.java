package skwhy.modules.CustomEntityElements.expressions;

import java.lang.reflect.Array;

import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import skwhy.customentity.CustomEntityManager;

/**
 * Un paramètre d'une définition d'entité custom, lu et écrit dans le fichier YAML de la définition. Les sous-classes déclarent les
 * chemins YAML de leurs patterns (dans l'ordre) ; pour un pattern à deux expressions la première est la clé ({@code equipment.<emplacement>},
 * {@code metadata.<index>}) et la dernière l'id de la définition.
 */
public abstract class ExprDefParam<T> extends SimpleExpression<T> {

    private final Class<T> type;
    private String path;
    private @Nullable Expression<?> keyExpr;
    private Expression<String> idExpr;
    private String description;

    protected ExprDefParam(Class<T> type) {
        this.type = type;
    }

    /** Chemins YAML ({@code scale}, {@code hitbox.width}, {@code equipment.}...) alignés sur les patterns ; un chemin qui finit par un point attend une clé. */
    protected abstract String[] paths();

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        path = paths()[matchedPattern];
        idExpr = (Expression<String>) exprs[exprs.length - 1];
        keyExpr = exprs.length == 2 ? exprs[0] : null;
        description = pr.expr;
        return true;
    }

    private @Nullable String resolve(Event event) {
        if (keyExpr == null) return path;
        Object key = keyExpr.getSingle(event);
        if (key == null) return null;
        return path + (key instanceof Number n ? String.valueOf(n.intValue()) : String.valueOf(key).trim().toLowerCase());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected @Nullable T[] get(Event event) {
        CustomEntityManager manager = CustomEntityManager.get();
        String id = idExpr.getSingle(event);
        String resolved = resolve(event);
        if (manager == null || id == null || resolved == null) return null;
        Object value = manager.param(id, resolved);
        if (value == null) return null;
        T[] result = (T[]) Array.newInstance(type, 1);
        result[0] = type.cast(value);
        return result;
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case SET -> new Class<?>[]{ type };
            case DELETE, RESET -> new Class<?>[0];
            default -> null;
        };
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        CustomEntityManager manager = CustomEntityManager.get();
        String id = idExpr.getSingle(event);
        String resolved = resolve(event);
        if (manager == null || id == null || resolved == null) return;
        if (mode == ChangeMode.SET) {
            if (delta == null || delta.length == 0) return;
            Object value = delta[0] instanceof Number n && type == Number.class ? (Object) n.doubleValue() : delta[0];
            manager.setParam(id, resolved, value);
        } else {
            manager.deleteParam(id, resolved);
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends T> getReturnType() { return type; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return description;
    }
}
