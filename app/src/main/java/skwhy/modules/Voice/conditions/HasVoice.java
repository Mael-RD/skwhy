package skwhy.modules.Voice.conditions;

import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import skwhy.modules.VoiceModule;
import skwhy.voice.VoiceListener;

public class HasVoice extends Condition {

    // ── Enregistrement de la condition (Nouvelle API Skript 2.14+) ────────────

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.CONDITION,
            SyntaxInfo.builder(HasVoice.class)
                .addPattern("%players% (has|have) voice")
                .addPattern("%players% (hasn't|haven't|do[es] not have|do[es]n't have) voice")
                .build()
        );
    }

    // ── Variables ─────────────────────────────────────────────────────────────

    private Expression<Player> playersExpr;

    // ── Initialisation ────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        this.playersExpr = (Expression<Player>) exprs[0];
        
        // matchedPattern == 0 correspond au pattern positif ("has voice")
        // matchedPattern == 1 correspond au pattern négatif ("hasn't voice")
        setNegated(matchedPattern == 1);
        
        return true;
    }

    // ── Vérification ──────────────────────────────────────────────────────────

    @Override
    public boolean check(Event event) {
        Player[] players = playersExpr.getArray(event);
        if (players.length == 0) return isNegated();

        VoiceListener listener = VoiceModule.getVoiceListener();
        if (listener == null) return isNegated();

        for (Player player : players) {
            boolean isListening = listener.isListening(player);
            
            // Si la condition attendue n'est pas remplie pour un seul joueur, on retourne false
            if (isListening == isNegated()) {
                return false;
            }
        }
        
        return true;
    }

    // ── Affichage (Debug Skript) ──────────────────────────────────────────────

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return playersExpr.toString(event, debug) + (isNegated() ? " hasn't voice" : " has voice");
    }
}