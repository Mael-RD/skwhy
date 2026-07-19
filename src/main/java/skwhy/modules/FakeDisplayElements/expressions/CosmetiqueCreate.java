package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.doc.RequiredPlugins;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.data.CosmetiqueData;

@Name("Create Cosmetic")
@Description("Creates a new cosmetic data object attached to an entity or player. When targeting a player, three booleans enable or disable the hat, back, and tail slots respectively. Non-player entities always have all three slots disabled.")
@Examples({
    "# Pattern 0: create a cosmetic for a non-player entity",
    "set {_cosmetique} to a new cosmetique for target entity",
    "",
    "# Pattern 1: create a cosmetic for a player with all slots enabled",
    "set {_cosmetique} to a new cosmetique for player with hats true, back true, and tail true",
    "",
    "# Pattern 1: create a cosmetic with only the hat slot enabled",
    "set {_cosmetique} to a new cosmetique for player with hats true back false, and tail false"
})
@Since("1.0.0")
@RequiredPlugins("PacketEvents")
public class CosmetiqueCreate extends SimpleExpression<CosmetiqueData> {

    private int patternIndex;
    
    // Variables pour le pattern 0
    private Expression<Entity> entityExpr;
    
    // Variables pour le pattern 1
    private Expression<Player> playerExpr;
    private Expression<Boolean> hatsExpr;
    private Expression<Boolean> backExpr;
    private Expression<Boolean> tailExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        this.patternIndex = matchedPattern;
        
        if (patternIndex == 0) {
            // Pattern avec juste %entity%
            this.entityExpr = (Expression<Entity>) exprs[0];
        } else {
            // Pattern avec %player% et les 3 booléens
            this.playerExpr = (Expression<Player>) exprs[0];
            this.hatsExpr = (Expression<Boolean>) exprs[1];
            this.backExpr = (Expression<Boolean>) exprs[2];
            this.tailExpr = (Expression<Boolean>) exprs[3];
        }
        return true;
    }
@Override
    protected @Nullable CosmetiqueData[] get(Event event) {
        if (patternIndex == 0) {
            Entity entity = entityExpr.getSingle(event);
            if (entity == null) return null;
            
            // --- NOUVELLE VÉRIFICATION ---
            // Si l'entité est en réalité un joueur, on bloque la création.
            // Le développeur Skript est obligé d'utiliser la syntaxe avec les booléens.
            if (entity instanceof Player) {
                return null; 
            }
            
            // Création avec les 3 booléens à false par défaut pour les non-joueurs
            return new CosmetiqueData[]{ new CosmetiqueData(entity, false, false, false) };
            
        } else {
            Player player = playerExpr.getSingle(event);
            if (player == null) return null;

            // Récupération des valeurs booléennes (avec false en cas de null par sécurité)
            Boolean hObj = hatsExpr.getSingle(event);
            Boolean bObj = backExpr.getSingle(event);
            Boolean tObj = tailExpr.getSingle(event);
            
            boolean hats = (hObj != null) ? hObj : false;
            boolean back = (bObj != null) ? bObj : false;
            boolean tail = (tObj != null) ? tObj : false;

            // Le joueur (Player) hérite de Entity, on le passe au constructeur
            return new CosmetiqueData[]{ new CosmetiqueData(player, hats, back, tail) };
        }
    }
    
    @Override
    public boolean isSingle() { 
        return true; 
    }

    @Override
    public Class<? extends CosmetiqueData> getReturnType() { 
        return CosmetiqueData.class; 
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        if (patternIndex == 0) {
            return "new cosmetique for " + entityExpr.toString(event, debug);
        } else {
            return "new cosmetique for " + playerExpr.toString(event, debug) + " with hats " + hatsExpr.toString(event, debug);
        }
    }

    public static void register(SkriptAddon addon) {
        // J'ai utilisé "[[,] [and]]" car c'est la façon native de Skript pour interpréter une virgule ou un "and" de manière optionnelle.
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(CosmetiqueCreate.class, CosmetiqueData.class)
                .addPattern("[a] [new] cosmetique for %entity%")
                .addPattern("[a] [new] cosmetique for %player% with [hat[s]] %boolean%[,] [back] %boolean%[[,] [and]] [tail] %boolean%")
                .build()
        );
    }
}