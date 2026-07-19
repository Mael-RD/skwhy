package skwhy.modules.NavigationElements.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.pathfinder.Navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Name("[Navigation] Creation")
@Description("Creates a new navigation object. Can be created from a numeric entity ID or a real entity. " +
    "Requires a hitbox vector (width/height), a starting location, and a movement type string (WALK, FLY, SWIM). " +
    "Speed and a viewer list are both optional. Note the the navigation will not start until you set the pause time.")
@Examples({
    "# Pattern 0: create from a numeric ID",
    "set {_navigation} to a new navigation with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player type \"WALK\"",
    "",
    "# Pattern 0: with optional speed and players",
    "set {_navigation} to a new navigation with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player type \"WALK\" speed 0.2 with players all players",
    "",
    "# Pattern 1: create from a real entity",
    "set {_navigation} to a new navigation with entity target entity type \"FLY\"",
    "",
    "# Pattern 1: with optional speed",
    "set {_navigation} to a new navigation with entity target entity type \"SWIM\" speed 0.15"
})
@Since("1.2.0")
public class NavigationCreate extends SimpleExpression<Navigation> {

    private Expression<Number>   idExpr;
    private Expression<Vector>   hitboxExpr;
    private Expression<Location> locationExpr;
    private Expression<String>   typeExpr;
    private Expression<Number>   speedExpr;
    private Expression<Player>   playersExpr;
    private Expression<Entity>   entityExpr;
    private int matchedPattern;

    /*
     * Slots dans exprs[] selon les patterns enregistrés :
     *
     * Pattern 0 : %number% %vector% %location% %string% [-%number%-] [-%players%-]
     *   exprs[0] = id        (Number)
     *   exprs[1] = hitbox    (Vector)
     *   exprs[2] = location  (Location)
     *   exprs[3] = type      (String)
     *   exprs[4] = speed     (Number)   — null si omis
     *   exprs[5] = players   (Player)   — null si omis
     *
     * Pattern 1 : %entity% %string% [-%number%-] [-%players%-]
     *   exprs[0] = entity    (Entity)
     *   exprs[1] = type      (String)
     *   exprs[2] = speed     (Number)   — null si omis
     *   exprs[3] = players   (Player)   — null si omis
     *
     * Les sections optionnelles sont écrites [-%type%-] (sans flag numéroté)
     * pour que Skript leur attribue un slot dans exprs[] et y place null quand
     * elles sont absentes, au lieu d'utiliser pr.mark qui ne remplit pas exprs.
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        this.matchedPattern = matchedPattern;

        if (matchedPattern == 0) {
            this.idExpr       = (Expression<Number>)   exprs[0];
            this.hitboxExpr   = (Expression<Vector>)   exprs[1];
            this.locationExpr = (Expression<Location>) exprs[2];
            this.typeExpr     = (Expression<String>)   exprs[3];
            this.speedExpr    = (Expression<Number>)   exprs[4]; // null si omis
            this.playersExpr  = (Expression<Player>)   exprs[5]; // null si omis
        } else {
            this.entityExpr  = (Expression<Entity>) exprs[0];
            this.typeExpr    = (Expression<String>) exprs[1];
            this.speedExpr   = (Expression<Number>) exprs[2]; // null si omis
        }

        return true;
    }

    @Override
    protected @Nullable Navigation[] get(Event event) {
        String rawType = typeExpr.getSingle(event);
        if (rawType == null) return null;

        // Vitesse : valeur optionnelle, 0.1 par défaut
        float speed = 0.1F;
        if (speedExpr != null) {
            Number speedValue = speedExpr.getSingle(event);
            if (speedValue != null) speed = speedValue.floatValue();
        }

        // Type de pathfinding
        Navigation.PathfindingType pathfindingType = Navigation.PathfindingType.WALK;
        try {
            pathfindingType = Navigation.PathfindingType.valueOf(
                rawType.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ignored) {
            // Garde WALK par défaut si la valeur est invalide
        }

        // Joueurs observateurs : optionnel
        List<Player> viewers = new ArrayList<>();
        if (playersExpr != null) {
            Player[] players = playersExpr.getAll(event);
            if (players != null) Collections.addAll(viewers, players);
        }

        if (matchedPattern == 0) {
            Number id = idExpr.getSingle(event);
            if (id == null) return null;
            Vector   hitbox   = hitboxExpr.getSingle(event);
            Location location = locationExpr.getSingle(event);
            if (hitbox == null || location == null) return null;
            return new Navigation[]{ new Navigation(id.intValue(), hitbox, location, pathfindingType, speed, viewers) };
        } else {
            Entity entity = entityExpr.getSingle(event);
            if (entity == null) return null;
            return new Navigation[]{ new Navigation(entity, speed, pathfindingType) };
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Navigation> getReturnType() { return Navigation.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        if (matchedPattern == 0) {
            StringBuilder sb = new StringBuilder("new navigation with id ");
            sb.append(idExpr.toString(event, debug))
              .append(" hitbox ").append(hitboxExpr.toString(event, debug))
              .append(" location ").append(locationExpr.toString(event, debug))
              .append(" type ").append(typeExpr.toString(event, debug));
            if (speedExpr   != null) sb.append(" speed ").append(speedExpr.toString(event, debug));
            if (playersExpr != null) sb.append(" with players ").append(playersExpr.toString(event, debug));
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder("new navigation with entity ");
            sb.append(entityExpr.toString(event, debug))
              .append(" type ").append(typeExpr.toString(event, debug));
            if (speedExpr   != null) sb.append(" speed ").append(speedExpr.toString(event, debug));
            return sb.toString();
        }
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(NavigationCreate.class, Navigation.class)
                // exprs : [0]number [1]vector [2]location [3]string [4]number? [5]players?
                .addPattern("[a] [new] navigation with id %number% hitbox %vector% location %location% type %string% [speed %-number%] [with players %-players%]")
                // exprs : [0]entity [1]string [2]number? [3]players?
                .addPattern("[a] [new] navigation with entity %entity% type %string% [speed %-number%]")
                .build()
        );
    }
}