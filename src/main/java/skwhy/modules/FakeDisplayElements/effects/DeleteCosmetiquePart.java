package skwhy.modules.FakeDisplayElements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.doc.RequiredPlugins;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.data.CosmetiqueData;

@Name("Delete Cosmetic Part")
@Description("Removes a specific part (hat, back, or tail) from a cosmetic. The hat pattern supports an optional slot identifier to remove only a specific hat layer.")
@Examples({
    "set {_cosmetique} to a new cosmetique for player",
    "",
    "# Remove a specific hat in a given slot",
    "delete the hat of {_cosmetique} in slot \"wings\"",
    "",
    "# Remove all hats at once",
    "remove the hat of {_cosmetique}",
    "",
    "# Remove the back part",
    "delete the back of {_cosmetique}",
    "",
    "# Remove the tail part",
    "remove the tail of {_cosmetique}"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class DeleteCosmetiquePart extends Effect {

    private int patternIndex;
    private Expression<CosmetiqueData> cosmetiqueExpr;
    private Expression<String> slotExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        this.patternIndex = matchedPattern;
        this.cosmetiqueExpr = (Expression<CosmetiqueData>) exprs[0];

        // Pour le pattern index 0 (hat), on regarde si le slot optionnel a été fourni
        if (patternIndex == 0) {
            // Si le bit 1 est présent dans pr.mark, l'expression de slot se trouve à l'index 1
            this.slotExpr = (pr.mark & 1) != 0 ? (Expression<String>) exprs[1] : null;
        }
        return true;
    }

    @Override
    protected void execute(Event event) {
        CosmetiqueData cosmetique = cosmetiqueExpr.getSingle(event);
        if (cosmetique == null) return;

        switch (patternIndex) {
            case 0 -> {
                String slot = slotExpr != null ? slotExpr.getSingle(event) : null;
                if (slot == null) cosmetique.removeHats();
                else cosmetique.removeHat(slot);
            }
            case 1 -> cosmetique.removeBack();
            case 2 -> cosmetique.removeTail();
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return switch (patternIndex) {
            case 0 -> "delete hat of " + cosmetiqueExpr.toString(event, debug) + (slotExpr != null ? " in slot " + slotExpr.toString(event, debug) : "");
            case 1 -> "delete back of " + cosmetiqueExpr.toString(event, debug);
            case 2 -> "delete tail of " + cosmetiqueExpr.toString(event, debug);
            default -> "delete cosmetic part";
        };
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(DeleteCosmetiquePart.class)
                // Pattern 0 (index 0) : Chapeau avec slot optionnel (assigné au bit 1 du pr.mark)
                .addPattern("(delete|remove) [the] hat of %cosmetique% [(1:in [slot] %-string%)]")
                // Pattern 1 (index 1) : Dos
                .addPattern("(delete|remove) [the] back of %cosmetique%")
                // Pattern 2 (index 2) : Queue
                .addPattern("(delete|remove) [the] tail of %cosmetique%")
                .build()
        );
    }
}