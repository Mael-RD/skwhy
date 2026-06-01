package skwhy.modules.FakePathFindingElements;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.FakePathFinding;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class FakePathFindingCreate extends SimpleExpression<FakePathFinding> {

    private Expression<Player> playerExpr;
    private Expression<String> typeExpr;
    private int patternIndex;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult pr) {
        this.patternIndex = matchedPattern;
        this.playerExpr = (Expression<Player>) exprs[0];
        if (patternIndex == 1) {
            this.typeExpr = (Expression<String>) exprs[1];
        }
        return true;
    }

    @Override
    protected @Nullable FakePathFinding[] get(Event event) {
        Player player = playerExpr.getSingle(event);
        if (player == null) {
            return null;
        }

        Location location = player.getLocation();
        Vector hitbox = new Vector(0.6, 1.8, 0.6);
        double speed = 0.2;
        List<Player> viewers = Collections.singletonList(player);

        FakePathFinding.PathfindingType pathfindingType = FakePathFinding.PathfindingType.WALK;
        if (patternIndex == 1) {
            String rawType = typeExpr.getSingle(event);
            if (rawType != null) {
                String normalized = rawType.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
                try {
                    pathfindingType = FakePathFinding.PathfindingType.valueOf(normalized);
                } catch (IllegalArgumentException ignored) {
                    pathfindingType = FakePathFinding.PathfindingType.WALK;
                }
            }
        }

        int entityId = SpigotReflectionUtil.generateEntityId();
        return new FakePathFinding[]{ new FakePathFinding(entityId, hitbox, location, speed, viewers, pathfindingType) };
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
        if (patternIndex == 0) {
            return "new fake pathfinding for " + playerExpr.toString(event, debug);
        }
        return "new fake pathfinding for " + playerExpr.toString(event, debug) + " with type " + typeExpr.toString(event, debug);
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(FakePathFindingCreate.class, FakePathFinding.class)
                .addPattern("[a] [new] fake pathfinding for %player%")
                .addPattern("[a] [new] fake pathfinding for %player% with [pathfinding] type %string%")
                .build()
        );
    }
}
