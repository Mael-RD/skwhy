package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.CosmetiqueData;

/**
 * Expression Skript pour accéder et modifier le facteur d'échelle (scale)
 * d'une instance de CosmetiqueData.
 *
 * Pattern: "[the] [cosmetic|cosmetique] scale of %cosmetique%"
 *
 * Applique le scale à tous les éléments du cosmétique (chapeaux, dos, queue).
 */
public class CosmetiqueScale extends SimpleExpression<Number> {

    private Expression<CosmetiqueData> cosmetiqueExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.cosmetiqueExpr = (Expression<CosmetiqueData>) exprs[0];
        return true;
    }

    @Override
    protected @Nullable Number[] get(Event event) {
        CosmetiqueData c = cosmetiqueExpr.getSingle(event);
        if (c == null) return null;

        return new Number[]{ c.getScale() };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        if (mode == ChangeMode.SET) return new Class<?>[]{ Number.class };
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || !(delta[0] instanceof Number n)) return;
        CosmetiqueData c = cosmetiqueExpr.getSingle(event);
        if (c == null) return;

        c.setScale(n.floatValue());
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Number> getReturnType() { return Number.class; }
    
    @Override
    public String toString(@Nullable Event event, boolean debug) {
        // On renvoie juste le texte représentant la syntaxe, pas sa valeur d'exécution.
        if (cosmetiqueExpr != null) {
            return "cosmetic scale of " + cosmetiqueExpr.toString(event, debug);
        }
        return "cosmetic scale";
    }
    
    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(CosmetiqueScale.class, Number.class)
                .addPattern("[the] [cosmetic|cosmetique] scale of %cosmetique%")
                .build()
        );
    }
}
