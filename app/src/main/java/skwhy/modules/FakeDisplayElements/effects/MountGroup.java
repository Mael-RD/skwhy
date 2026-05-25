package skwhy.modules.FakeDisplayElements.effects;

import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.util.Kleenean;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.data.DisplayGroupData;
import skwhy.data.CosmetiqueData;

import java.util.Arrays;
import java.util.List;

public class MountGroup extends Effect {

    private Expression<Object> targetsExpr;
    private @Nullable Expression<Object> vehicleExpr;
    private @Nullable Expression<Location> locationExpr;
    private @Nullable Expression<Player> playersExpr;
    
    private int matchedPattern;
    private boolean isDismount;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, SkriptParser.ParseResult pr) {
        this.matchedPattern = matchedPattern;
        this.targetsExpr = (Expression<Object>) exprs[0];

        if (matchedPattern == 1) {
            // Pattern 1: mount on %entity%
            this.vehicleExpr = (Expression<Object>) exprs[1];
        } else if (matchedPattern == 2) {
            // Pattern 2: mount on %integer% at %location%
            this.vehicleExpr = (Expression<Object>) exprs[1];
            this.locationExpr = (Expression<Location>) exprs[2];
        } else if (matchedPattern == 3) {
            // Pattern 3: [dis]mount on %entity/integer% for %players%
            this.vehicleExpr = (Expression<Object>) exprs[1];
            this.playersExpr = (Expression<Player>) exprs[2];
            this.isDismount = pr.mark == 1; 
        }
        
        return true;
    }

    @Override
    protected void execute(Event event) {
        Object[] targets = targetsExpr.getArray(event);
        if (targets == null || targets.length == 0) return;

        // Pattern 0 : make displays %displaygroups/cosmetiques% mount
        if (matchedPattern == 0) {
            if (targets.length == 1 && targets[0] instanceof CosmetiqueData cosme) {
                cosme.mount();
                return;
            }

            for (Object target : targets) {
                if (target instanceof DisplayGroupData group) {
                    group.mount();
                }
            }
            return;
        }

        // Pattern 1 : make fake %displaygroups% mount on %entity%
        if (matchedPattern == 1 && vehicleExpr != null) {
            Entity vehicle = (Entity) vehicleExpr.getSingle(event);
            if (vehicle == null) return;

            for (Object target : targets) {
                if (target instanceof DisplayGroupData group) {
                    group.setAttachedEntity(vehicle);
                }
            }
            return;
        }

        // Pattern 2 : make fake %displaygroups% mount on %integer% at %location%
        if (matchedPattern == 2 && vehicleExpr != null && locationExpr != null) {
            Number vehicleIdNum = (Number) vehicleExpr.getSingle(event);
            Location loc = locationExpr.getSingle(event);
            if (vehicleIdNum == null || loc == null) return;
            
            int vehicleId = vehicleIdNum.intValue();

            for (Object target : targets) {
                if (target instanceof DisplayGroupData group) {
                    group.setAttachedId(loc, vehicleId);
                }
            }
            return;
        }

        // Pattern 3 : make fake %entities/integers% [dis]mount on %entity/integer% for %players%
        if (matchedPattern == 3 && vehicleExpr != null && playersExpr != null) {
            Object vehicleObj = vehicleExpr.getSingle(event);
            Player[] players = playersExpr.getArray(event);
            
            if (vehicleObj == null || players == null || players.length == 0) return;

            List<Player> playerList = Arrays.asList(players);

            for (Object target : targets) {
                int targetId = getEntityId(target);
                if (targetId == -1) continue;

                if (vehicleObj instanceof Entity vehicleEntity) {
                    // Le véhicule est une entité physique
                    if (isDismount) {
                        DisplayGroupData.removeOtherMount(vehicleEntity, targetId, playerList);
                    } else {
                        DisplayGroupData.addOtherMount(vehicleEntity, targetId, playerList);
                    }
                } else if (vehicleObj instanceof Number vehicleNum) {
                    // Le véhicule est un identifiant virtuel
                    int vehicleId = vehicleNum.intValue();
                    if (isDismount) {
                        DisplayGroupData.removeOtherMount(vehicleId, targetId, playerList);
                    } else {
                        DisplayGroupData.addOtherMount(vehicleId, targetId, playerList);
                    }
                }
            }
        }
    }

    /**
     * Helper pour extraire de manière sécurisée l'ID de l'entité passagère (la cible)
     */
    private int getEntityId(Object obj) {
        if (obj instanceof Entity entity) {
            return entity.getEntityId();
        } else if (obj instanceof Number number) {
            return number.intValue();
        }
        return -1;
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        String base = "make fake " + targetsExpr.toString(event, debug);
        if (matchedPattern == 0) {
            return "make displays " + targetsExpr.toString(event, debug) + " mount";
        } else if (matchedPattern == 1) {
            return base + " mount on " + (vehicleExpr != null ? vehicleExpr.toString(event, debug) : "null");
        } else if (matchedPattern == 2) {
            return base + " mount on " + (vehicleExpr != null ? vehicleExpr.toString(event, debug) : "null") 
                    + " at " + (locationExpr != null ? locationExpr.toString(event, debug) : "null");
        } else {
            return base + (isDismount ? " dismount on " : " mount on ") 
                    + (vehicleExpr != null ? vehicleExpr.toString(event, debug) : "null")
                    + " for " + (playersExpr != null ? playersExpr.toString(event, debug) : "null");
        }
    }

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(MountGroup.class)
                // 0
                .addPattern("make fake %displaygroups/cosmetiques% mount")
                // 1
                .addPattern("make fake %displaygroups% mount on %entity%")
                // 2
                .addPattern("make fake %displaygroups% mount on %integer% at %location%")
                // 3
                .addPattern("make fake %entities/integers% [(1:dis)]mount on %entity/integer% for %players%")
                .build()
        );
    }
}