package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.data.CosmetiqueData;

public class CosmetiqueSelfVisibility extends SimpleExpression<Boolean> {

    private Expression<CosmetiqueData> cosmetiqueExpr;
    // 0 = hats, 1 = back, 2 = tail
    private int partIndex; 

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        this.cosmetiqueExpr = (Expression<CosmetiqueData>) exprs[0];
        // On récupère la valeur définie dans le pattern (0, 1 ou 2)
        this.partIndex = pr.mark;
        return true;
    }

    @Override
    protected @Nullable Boolean[] get(Event event) {
        CosmetiqueData cosme = cosmetiqueExpr.getSingle(event);
        if (cosme == null) return null;

        boolean result = switch (partIndex) {
            case 0 -> cosme.getSelfHats();
            case 1 -> cosme.getSelfBack();
            case 2 -> cosme.getSelfTail();
            default -> false;
        };

        return new Boolean[]{ result };
    }

    @Override
    public @Nullable Class<?>[] acceptChange(ChangeMode mode) {
        // On accepte uniquement de SET (définir) avec un booléen
        if (mode == ChangeMode.SET) {
            return new Class<?>[]{ Boolean.class };
        }
        return null;
    }

    @Override
    public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
        if (mode != ChangeMode.SET || delta == null || delta[0] == null) return;
        
        CosmetiqueData cosme = cosmetiqueExpr.getSingle(event);
        if (cosme == null) return;

        boolean value = (Boolean) delta[0];

        // On modifie la bonne variable selon la partie demandée
        switch (partIndex) {
            case 0 -> cosme.setSelfHats(value);
            case 1 -> cosme.setSelfBack(value);
            case 2 -> cosme.setSelfTail(value);
        }
    }

    @Override
    public boolean isSingle() { 
        return true; 
    }

    @Override
    public Class<? extends Boolean> getReturnType() { 
        return Boolean.class; 
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        String partName = switch (partIndex) {
            case 0 -> "hats";
            case 1 -> "back";
            case 2 -> "tail";
            default -> "unknown";
        };
        return partName + " visibility in " + cosmetiqueExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(CosmetiqueSelfVisibility.class, Boolean.class)
                // Le système de mark (0:, 1:, 2:) assigne cette valeur à `pr.mark` dans le init()
                .addPattern("[the] (0:hat[s]|1:back|2:tail) visibility (in|of) %cosmetique%")
                .build()
        );
    }
}