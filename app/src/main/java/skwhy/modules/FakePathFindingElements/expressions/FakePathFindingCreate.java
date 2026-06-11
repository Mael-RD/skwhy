package skwhy.modules.FakePathFindingElements.expressions;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import org.bukkit.Location;
import org.bukkit.entity.Player;
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

public class FakePathFindingCreate extends SimpleExpression<FakePathFinding> {

    private Expression<Number> idExpr;
    private Expression<Vector> hitboxExpr;
    private Expression<Location> locationExpr;
    private Expression<String> typeExpr;
    private Expression<Number> speedExpr;
    private Expression<Player> playersExpr;
    private int mark;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        this.idExpr = (Expression<Number>) exprs[0];
        this.hitboxExpr = (Expression<Vector>) exprs[1];
        this.locationExpr = (Expression<Location>) exprs[2];
        this.typeExpr = (Expression<String>) exprs[3];
        this.mark = pr.mark;

        int nextIndex = 4;
        if ((mark & 1) != 0) {
            this.speedExpr = (Expression<Number>) exprs[nextIndex++];
        }
        if ((mark & 2) != 0) {
            this.playersExpr = (Expression<Player>) exprs[nextIndex];
        }
        return true;
    }

    @Override
    protected @Nullable FakePathFinding[] get(Event event) {
        Number entityIdNumber = idExpr.getSingle(event);
        Vector hitbox = hitboxExpr.getSingle(event);
        Location location = locationExpr.getSingle(event);
        String rawType = typeExpr.getSingle(event);

        if (entityIdNumber == null || hitbox == null || location == null || rawType == null) {
            return null;
        }

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

        return new FakePathFinding[]{
            new FakePathFinding(entityIdNumber.intValue(), hitbox, location, speed, viewers, pathfindingType)
        };
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
                .build()
        );
    }
}
