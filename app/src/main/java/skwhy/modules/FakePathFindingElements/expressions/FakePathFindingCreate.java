package skwhy.modules.FakePathFindingElements.expressions;

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
import skwhy.data.FakePathFinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Name("Create Fake Pathfinding")
@Description("Creates a new fake pathfinding object. Can be created from a numeric entity ID or a real entity. " +
    "Requires a hitbox vector (width/height), a starting location, and a movement type string (WALK, FLY, SWIM). " +
    "Speed and a viewer list are both optional.")
@Examples({
    "# Pattern 0: create from a numeric ID",
    "set {_fake} to a new fake pathfinding with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player type \"WALK\"",
    "",
    "# Pattern 0: with optional speed and players",
    "set {_fake} to a new fake pathfinding with id 12345 hitbox vector(0.6, 1.8, 0.6) location location of player type \"WALK\" speed 0.2 with players all players",
    "",
    "# Pattern 1: create from a real entity",
    "set {_fake} to a new fake pathfinding with entity target entity hitbox vector(0.6, 1.8, 0.6) location location of player type \"FLY\"",
    "",
    "# Pattern 1: with optional speed and players",
    "set {_fake} to a new fake pathfinding with entity target entity hitbox vector(0.6, 1.8, 0.6) location location of player type \"SWIM\" speed 0.15 with players player"
})
@Since("1.2.0")
public class FakePathFindingCreate extends SimpleExpression<FakePathFinding> {

    private Expression<Number> idExpr;
    private Expression<Vector> hitboxExpr;
    private Expression<Location> locationExpr;
    private Expression<String> typeExpr;
    private Expression<Number> speedExpr;
    private Expression<Player> playersExpr;
    private Expression<Entity> entityExpr;
    private int matchedPattern;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        this.matchedPattern = matchedPattern; // stocke le pattern utilisé

        if (matchedPattern == 0) {
            // Pattern 0: 6 arguments au total
            this.idExpr = (Expression<Number>) exprs[0];
            this.hitboxExpr = (Expression<Vector>) exprs[1];
            this.locationExpr = (Expression<Location>) exprs[2];
            this.typeExpr = (Expression<String>) exprs[3];
            
            // Les index 4 et 5 sont fixes, que l'utilisateur les tape ou non.
            this.speedExpr = (Expression<Number>) exprs[4];
            this.playersExpr = (Expression<Player>) exprs[5];
            
        } else {
            // Pattern 1: 3 arguments au total
            this.entityExpr = (Expression<Entity>) exprs[0];
            this.typeExpr = (Expression<String>) exprs[1];
            
            // L'index 2 est fixe
            this.speedExpr = (Expression<Number>) exprs[2];
        }

        return true;
    }

    @Override
    protected @Nullable FakePathFinding[] get(Event event) {
        String rawType = typeExpr.getSingle(event);

        double speed = 0.1;
        if (speedExpr != null) {
            Number speedValue = speedExpr.getSingle(event);
            if (speedValue != null) {
                speed = speedValue.doubleValue();
            }
        }

        String normalized = rawType.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        FakePathFinding.PathfindingType pathfindingType = FakePathFinding.PathfindingType.WALK;
        try {
            pathfindingType = FakePathFinding.PathfindingType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            // Keep default WALK if parsing fails.
        }

        List<Player> viewers = new ArrayList<>();
        if (playersExpr != null) {
            Player[] players = playersExpr.getAll(event);
            if (players != null && players.length > 0) {
                Collections.addAll(viewers, players);
            }
        }

        if (matchedPattern == 0) {
            // Pattern id numérique (comportement existant)
            Vector hitbox = hitboxExpr.getSingle(event);
            Location location = locationExpr.getSingle(event);
            Number id = idExpr.getSingle(event);
            if (id == null) return null;
            return new FakePathFinding[]{ new FakePathFinding(id.intValue(), hitbox, location, speed, viewers, pathfindingType) };
        } else {
            // Pattern vraie entité
            Entity entity = entityExpr.getSingle(event);
            if (entity == null) return null;
            return new FakePathFinding[]{ new FakePathFinding(entity, speed, viewers, pathfindingType) };
        }
    }

    @Override
    public boolean isSingle() {
        return true;
    }

    @Override
    public Class<? extends FakePathFinding> getReturnType() {
        return FakePathFinding.class;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        StringBuilder sb = new StringBuilder("new fake pathfinding with id ");
        sb.append(idExpr.toString(event, debug))
            .append(" hitbox ").append(hitboxExpr.toString(event, debug))
            .append(" location ").append(locationExpr.toString(event, debug))
            .append(" type ").append(typeExpr.toString(event, debug));
        if (speedExpr != null) {
            sb.append(" speed ").append(speedExpr.toString(event, debug));
        }
        if (playersExpr != null) {
            sb.append(" with players ").append(playersExpr.toString(event, debug));
        }
        return sb.toString();
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(FakePathFindingCreate.class, FakePathFinding.class)
                .addPattern("[a] [new] fake pathfinding with id %number% hitbox %vector% location %location% type %string% [(1:speed %number%)] [(2:with players %-players%)]")
                .addPattern("[a] [new] fake pathfinding with entity %entity% type %string% [(1:speed %number%)]")
                .build()
        );
    }
}
