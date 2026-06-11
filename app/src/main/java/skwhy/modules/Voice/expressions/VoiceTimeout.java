package skwhy.modules.Voice.expressions;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.util.Timespan;
import ch.njol.skript.util.Timespan.TimePeriod;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;
import skwhy.modules.VoiceModule;
import skwhy.voice.StreamingSpeechSession;

/**
 * Expression : voice max timeout of %player%
 *
 * Durée maximale pendant laquelle une ArmedWindow reste ouverte
 * en attente du payload après détection d'un trigger.
 * Retourne et accepte un Timespan Skript.
 *
 * ── Syntaxe Skript ────────────────────────────────────────────────────────────
 *
 *  GET
 *    set {_t} to voice max timeout of player
 *    send "Timeout actuel : %voice max timeout of player%" to player
 *
 *  SET  (Timespan Skript → long ms en interne)
 *    set voice max timeout of player to 3 seconds
 *    set voice max timeout of player to 500 milliseconds
 *    set voice max timeout of player to 1 minute
 *
 *  DELETE / RESET  (remet à la valeur par défaut : 3000 ms)
 *    delete voice max timeout of player
 *    reset voice max timeout of player
 *
 * ── Conversion Timespan ───────────────────────────────────────────────────────
 *  On utilise timespan.getAs(TimePeriod.MILLISECOND) — API non-dépréciée depuis 2.10.
 *  getMilliSeconds() est marqué @Deprecated(since="2.10.0", forRemoval=true).
 *
 * ── Stockage ──────────────────────────────────────────────────────────────────
 *  La valeur est stockée directement dans StreamingSpeechSession (champ volatile long).
 *  Pas de map externe, pas de persistence — simple et léger.
 */

public class VoiceTimeout extends SimpleExpression<Timespan> {

    /** Valeur par défaut si non définie ou après reset */
    static final long DEFAULT_TIMEOUT_MS = 3000L;

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            SyntaxInfo.Expression.builder(VoiceTimeout.class, Timespan.class)
                .addPattern("voice max timeout of %player%")
                .addPattern("%player%'[s] voice max timeout")
                .build()
        );
    }

    private Expression<Player> playerExpr;

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern,
                        Kleenean isDelayed, ParseResult parseResult) {
        playerExpr = (Expression<Player>) exprs[0];
        return true;
    }

    // ── GET ───────────────────────────────────────────────────────────────────

    @Override
    protected @Nullable Timespan[] get(Event event) {
        Player player = playerExpr.getSingle(event);
        if (player == null) return new Timespan[0];

        StreamingSpeechSession session =
                VoiceModule.getVoiceListener().getSession(player);

        long ms = (session != null)
                ? session.getWindowTimeoutMs()
                : DEFAULT_TIMEOUT_MS;

        // Construire un Timespan depuis des millisecondes
        // new Timespan(long millis) — constructeur principal non-déprécié
        return new Timespan[]{ new Timespan(ms) };
    }

    @Override
    public boolean isSingle() { return true; }

    @Override
    public Class<? extends Timespan> getReturnType() { return Timespan.class; }

    // ── CHANGERS ──────────────────────────────────────────────────────────────

    @Override
    public Class<?>[] acceptChange(ChangeMode mode) {
        return switch (mode) {
            case SET            -> new Class[]{ Timespan.class };
            case DELETE, RESET  -> new Class[0]; // remet le défaut
            default             -> null;
        };
    }

    @Override
    public void change(Event event, Object[] delta, ChangeMode mode) {
        Player player = playerExpr.getSingle(event);
        if (player == null) return;

        StreamingSpeechSession session =
                VoiceModule.getVoiceListener().getSession(player);
        if (session == null) return;

        switch (mode) {
            case SET -> {
                if (delta == null || delta.length == 0 || !(delta[0] instanceof Timespan ts)) return;
                // Conversion Timespan → ms via l'API non-dépréciée (Skript ≥ 2.10)
                long ms = ts.getAs(TimePeriod.MILLISECOND);
                if (ms < 0) {
                    return;
                }
                session.setWindowTimeoutMs(ms);
            }
            case DELETE, RESET, REMOVE_ALL -> {
                session.setWindowTimeoutMs(DEFAULT_TIMEOUT_MS);
            }
            default -> {
                throw new UnsupportedOperationException("Change mode " + mode + " is not supported for voice max timeout");
            }
        }
    }

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return "voice max timeout of " + playerExpr.toString(event, debug);
    }
}