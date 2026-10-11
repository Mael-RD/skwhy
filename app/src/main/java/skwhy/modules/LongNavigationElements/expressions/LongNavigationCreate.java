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
import skwhy.chunkpath.longnav.LongNavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Name("[Long Navigation] Creation")
@Description("Creates a new long-distance navigation object. Can be created from a numeric entity ID or a real " +
    "entity. Requires a hitbox vector (width/height) and a starting location for the virtual pattern; only a " +
    "speed is optional. Unlike a normal navigation, there is no movement type: long navigations are always " +
    "ground-based. It will not start moving until an end location is set (see 'end location of').")
@Examples({
    "# Pattern 0: create from a numeric ID",
    "set {_longnav} to a new long navigation with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player",
    "",
    "# Pattern 1: create from a real entity",
    "set {_longnav} to a new long navigation with entity target entity speed 0.2"
})
@Since("1.5.0")
public class LongNavigationCreate extends SimpleExpression<LongNavigation> {

    private Expression<Number>   idExpr;
    private Expression<Vector>   hitboxExpr;
    private Expression<Location> locationExpr;
    private Expression<Number>   speedExpr;
    private Expression<Player>   playersExpr;
    private Expression<Entity>   entityExpr;
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
        } else {
            this.entityExpr  = (Expression<Entity>) exprs[0];
            this.speedExpr   = (Expression<Number>) exprs[1]; // null si omis
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
            return new LongNavigation[]{ new LongNavigation(id.intValue(), hitbox, location, speed, viewers) };
        } else {
            Entity entity = entityExpr.getSingle(event);
            if (entity == null) return null;
            return new LongNavigation[]{ new LongNavigation(entity, speed) };
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
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder("new long navigation with entity ");
            sb.append(entityExpr.toString(event, debug));
            if (speedExpr != null) sb.append(" speed ").append(speedExpr.toString(event, debug));
            return sb.toString();
        }
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(LongNavigationCreate.class, LongNavigation.class)
                // exprs : [0]number [1]vector [2]location [3]number? [4]players?
                .addPattern("[a] [new] long navigation with id %number% hitbox %vector% location %location% [speed %-number%] [with players %-players%]")
                // exprs : [0]entity [1]number?
                .addPattern("[a] [new] long navigation with entity %entity% [speed %-number%]")
                .build()
        );
    }
}
