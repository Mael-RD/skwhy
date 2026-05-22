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

import skwhy.data.CosmetiqueData;
import skwhy.data.DisplayGroupData;
import skwhy.data.Tail.TailNode;

public class SetCosmetiquePart extends Effect {

    private int patternIndex;

    private Expression<CosmetiqueData> cosmetiqueExpr;
    private Expression<DisplayGroupData> displayGroupExpr; // Utilisé pour hat et back
    private Expression<TailNode> tailNodeExpr;             // Utilisé pour tail
    
    // Variables spécifiques au chapeau (hat)
    private Expression<String> slotExpr;
    private Expression<Boolean> verticalRotExpr;
    private Expression<Boolean> horizontalRotExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.patternIndex = matchedPattern;
        this.cosmetiqueExpr = (Expression<CosmetiqueData>) exprs[0];

        if (patternIndex == 0) {
            // Pattern 0: set hat ...
            this.displayGroupExpr = (Expression<DisplayGroupData>) exprs[1];
            this.slotExpr = (Expression<String>) exprs[2];
            this.verticalRotExpr = (Expression<Boolean>) exprs[3];
            this.horizontalRotExpr = (Expression<Boolean>) exprs[4];
        } 
        else if (patternIndex == 1) {
            // Pattern 1: set back ...
            this.displayGroupExpr = (Expression<DisplayGroupData>) exprs[1];
            this.slotExpr = (Expression<String>) exprs[2];
        } 
        else if (patternIndex == 2) {
            // Pattern 2: set tail ...
            this.tailNodeExpr = (Expression<TailNode>) exprs[1];
        }

        return true;
    }

    @Override
    protected void execute(Event event) {
        CosmetiqueData cosmetique = cosmetiqueExpr.getSingle(event);
        if (cosmetique == null) return;

        if (patternIndex == 0) {
            if (displayGroupExpr == null || slotExpr == null || verticalRotExpr == null || horizontalRotExpr == null) return;
            DisplayGroupData group = displayGroupExpr.getSingle(event);
            String slot = slotExpr.getSingle(event);
            Boolean vRot = verticalRotExpr.getSingle(event);
            Boolean hRot = horizontalRotExpr.getSingle(event);

            if (group != null && slot != null && vRot != null && hRot != null) {
                cosmetique.setHat(group, slot, vRot, hRot);
            }
        } 
        else if (patternIndex == 1) {
            if (displayGroupExpr == null || slotExpr == null) return;
            DisplayGroupData group = displayGroupExpr.getSingle(event);
            String type = slotExpr.getSingle(event);
            if (group != null && type != null) {
                cosmetique.setBack(group, type);
            }
        } 
        else if (patternIndex == 2) {
            if (tailNodeExpr == null) return;
            TailNode tail = tailNodeExpr.getSingle(event);
            if (tail != null) {
                cosmetique.setTail(tail);
            }
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        if (patternIndex == 0) {
            return "set hat of " + cosmetiqueExpr.toString(event, debug) + " to " + displayGroupExpr.toString(event, debug);
        } else if (patternIndex == 1) {
            return "set back of " + cosmetiqueExpr.toString(event, debug) + " to " + displayGroupExpr.toString(event, debug);
        } else {
            return "set tail of " + cosmetiqueExpr.toString(event, debug) + " to " + tailNodeExpr.toString(event, debug);
        }
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(SetCosmetiquePart.class)
                // Pattern 0 (index 0)
                .addPattern("set hat of %cosmetique% to %displaygroup% in [slot] %string% with [[vertical] rot[ation]] %boolean%[[,] [and] [[horizontal] rot[ation]]] %boolean%")
                // Pattern 1 (index 1)
                .addPattern("set back of %cosmetique% to %displaygroup% [with type] %string%")
                // Pattern 2 (index 2)
                .addPattern("set tail of %cosmetique% to %tailpart%")
                .build()
        );
    }
}