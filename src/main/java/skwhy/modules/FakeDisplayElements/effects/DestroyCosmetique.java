package skwhy.modules.FakeDisplayElements.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.doc.RequiredPlugins;

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

@Name("Destroy Cosmetic")
@Description("Fully destroys one or more cosmetics and removes them from all targeted players. Calls the internal delete method on each cosmetic object.")
@Examples({
    "set {_cosmetique} to a new cosmetique for player",
    "",
    "# Destroy a single cosmetic",
    "destroy cosmetique {_cosmetique}",
    "",
    "# Destroy a list of cosmetics",
    "destroy cosmetique {_cosmetiques::*}"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class DestroyCosmetique extends Effect {

    private Expression<CosmetiqueData> cosmetiqueExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        cosmetiqueExpr = (Expression<CosmetiqueData>) exprs[0];
        return true;
    }

    @Override
    protected void execute(Event event) {
        CosmetiqueData[] cosmetiques = cosmetiqueExpr.getAll(event);
        if (cosmetiques == null) return;
        for (CosmetiqueData cosmetique : cosmetiques) {
            cosmetique.delete();
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "destroy cosmetique " + cosmetiqueExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(DestroyCosmetique.class)
                .addPattern("destroy [cosmetique] %cosmetiques%")
                .build()
        );
    }
}