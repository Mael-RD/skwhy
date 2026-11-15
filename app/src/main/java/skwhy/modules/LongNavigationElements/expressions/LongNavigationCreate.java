package skwhy.modules.LongNavigationElements.expressions;

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
import skwhy.chunkpath.block.PathCostRules;
import skwhy.chunkpath.longnav.LongNavigation;
import skwhy.modules.LongNavigationElements.types.PathCostRuleData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Name("[Long Navigation] Creation")
@Description("Creates a new long-distance navigation object. Can be created from a numeric entity ID or a real " +
    "entity. Requires a hitbox vector (width/height) and a starting location for the virtual pattern; only a " +
    "speed is optional. Unlike a normal navigation, there is no movement type: long navigations are always " +
    "ground-based. A new long navigation is paused: it only starts walking once an end location is set AND " +
    "its pause ticks are set to 0 (see 'end location of' and 'pause ticks of'). The speed is the distance walked " +
    "per tick in blocks (default 0.1, so 2 blocks per second). While a real entity is being driven, its AI and gravity " +
    "are switched off; they are given back when it arrives, when no route exists, or when the navigation is destroyed. " +
    "The optional 'using rules' takes path cost rules for the walking itself: a rule without block or tag is the " +
    "default rule, and a block covered by no rule is not walkable. There are no default rules: without any rule the " +
    "navigation logs a warning and does nothing when it should start. The route between chunks keeps the costs of the chunk path records (see 'register chunk path'). " +
    "Change them later with 'path cost rules of long navigation'. " +
    "A navigation made with a player as the entity never drives it (no teleport, no gravity change, no pause, no speed): " +
    "it only answers 'blocks of long navigation', to get the path ahead of the player. " +
    "With 'for <players>' the real entity is driven virtually: the walk is simulated and sent as packets to these players only, " +
    "the entity itself never moves on the server (the others keep seeing it where it is; the client loses it when it leaves its " +
    "tracking range, so prefer an id-based navigation for a long trip). " +
    "A security allows one navigation per entity: a real one (the entity is moved) is alone on it; several virtual ones (by id, or 'for') " +
    "can share an entity if they have no player in common, never together with a real one. A refused creation logs an error in the " +
    "console and returns nothing; config 'long_navigation.allow_multiple: true' lifts the security.")
@Examples({
    "# Pattern 0: create from a numeric ID",
    "set {_longnav} to a new long navigation with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player",
    "",
    "# Pattern 1: create from a real entity, with path cost rules (required to walk):",
    "# roads are cheap, everything else costs 5, water edges are not walkable",
    "set {_road} to a new path cost rule with cost 1",
    "add stone bricks to blocks of path cost rule {_road}",
    "set {_other} to a new path cost rule with cost 5",
    "set {_longnav} to a new long navigation with entity target entity speed 0.2 using rules {_road} and {_other}",
    "",
    "# Pattern 1 with players: the entity is shown walking to the player only, it stays where it is for the others",
    "set {_longnav} to a new long navigation with entity target entity speed 0.2 for player using rules {_road} and {_other}"
})
@Since("1.5.0, 1.6.1 (for <players>, security)")
public class LongNavigationCreate extends SimpleExpression<LongNavigation> {

    private Expression<Number>   idExpr;
    private Expression<Vector>   hitboxExpr;
    private Expression<Location> locationExpr;
    private Expression<Number>   speedExpr;
    private Expression<Player>   playersExpr;
    private Expression<Entity>   entityExpr;
    private Expression<PathCostRuleData> rulesExpr;
    private int matchedPattern;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        this.matchedPattern = matchedPattern;

        if (matchedPattern == 0) {
            this.idExpr       = (Expression<Number>)   exprs[0];
            this.hitboxExpr   = (Expression<Vector>)   exprs[1];
            this.locationExpr = (Expression<Location>) exprs[2];
            this.speedExpr    = (Expression<Number>)   exprs[3]; // null si omis
            this.playersExpr  = (Expression<Player>)   exprs[4]; // null si omis
            this.rulesExpr    = (Expression<PathCostRuleData>) exprs[5]; // null si omis
        } else {
            this.entityExpr  = (Expression<Entity>) exprs[0];
            this.speedExpr   = (Expression<Number>) exprs[1]; // null si omis
            this.playersExpr = (Expression<Player>) exprs[2]; // null si omis
            this.rulesExpr   = (Expression<PathCostRuleData>) exprs[3]; // null si omis
        }

        return true;
    }

    @Override
    protected @Nullable LongNavigation[] get(Event event) {
        float speed = 0.1F;
        if (speedExpr != null) {
            Number speedValue = speedExpr.getSingle(event);
            if (speedValue != null) speed = speedValue.floatValue();
        }

        PathCostRules rules = rulesExpr != null ? PathCostRuleData.buildRules(rulesExpr.getAll(event)) : null;

        try {
            return create(event, speed, rules);
        } catch (skwhy.chunkpath.longnav.NavigationGuard.Refused e) {
            // jamais d'exception vers le script : erreur en console, pas de navigation
            skwhy.SkWhy.getInstance().getLogger().severe("[ChunkPath] Long navigation refusée : " + e.getMessage()
                    + " (long_navigation.allow_multiple: true pour lever cette sécurité)");
            return null;
        }
    }

    private @Nullable LongNavigation[] create(Event event, float speed, PathCostRules rules) {
        if (matchedPattern == 0) {
            Number id = idExpr.getSingle(event);
            if (id == null) return null;
            Vector   hitbox   = hitboxExpr.getSingle(event);
            Location location = locationExpr.getSingle(event);
            if (hitbox == null || location == null) return null;

            List<Player> viewers = new ArrayList<>();
            if (playersExpr != null) {
                Player[] players = playersExpr.getAll(event);
                if (players != null) Collections.addAll(viewers, players);
            }
            return new LongNavigation[]{ new LongNavigation(id.intValue(), hitbox, location, speed, viewers, rules) };
        } else {
            Entity entity = entityExpr.getSingle(event);
            if (entity == null) return null;
            if (playersExpr != null) { // pilotée virtuellement : l'entité ne bouge pas, ces joueurs la voient marcher
                Player[] players = playersExpr.getAll(event);
                List<Player> viewers = new ArrayList<>();
                if (players != null) Collections.addAll(viewers, players);
                return new LongNavigation[]{ LongNavigation.virtualOf(entity, speed, rules, viewers) };
            }
            return new LongNavigation[]{ new LongNavigation(entity, speed, rules) };
        }
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends LongNavigation> getReturnType() { return LongNavigation.class; }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        if (matchedPattern == 0) {
            StringBuilder sb = new StringBuilder("new long navigation with id ");
            sb.append(idExpr.toString(event, debug))
              .append(" hitbox ").append(hitboxExpr.toString(event, debug))
              .append(" location ").append(locationExpr.toString(event, debug));
            if (speedExpr   != null) sb.append(" speed ").append(speedExpr.toString(event, debug));
            if (playersExpr != null) sb.append(" with players ").append(playersExpr.toString(event, debug));
            if (rulesExpr   != null) sb.append(" using rules ").append(rulesExpr.toString(event, debug));
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder("new long navigation with entity ");
            sb.append(entityExpr.toString(event, debug));
            if (speedExpr != null) sb.append(" speed ").append(speedExpr.toString(event, debug));
            if (playersExpr != null) sb.append(" for ").append(playersExpr.toString(event, debug));
            if (rulesExpr != null) sb.append(" using rules ").append(rulesExpr.toString(event, debug));
            return sb.toString();
        }
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(LongNavigationCreate.class, LongNavigation.class)
                // exprs : [0]number [1]vector [2]location [3]number? [4]players? [5]rules?
                .addPattern("[a] [new] long navigation with id %number% hitbox %vector% location %location% [speed %-number%] [with players %-players%] [using rules %-pathcostrules%]")
                // exprs : [0]entity [1]number? [2]players? [3]rules?
                .addPattern("[a] [new] long navigation with entity %entity% [speed %-number%] [for %-players%] [using rules %-pathcostrules%]")
                .build()
        );
    }
}
