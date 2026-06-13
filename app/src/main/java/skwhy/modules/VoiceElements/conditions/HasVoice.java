package skwhy.modules.VoiceElements.conditions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.RequiredPlugins;
import ch.njol.skript.doc.Since;

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
import org.skriptlang.skript.docs.Origin;

import skwhy.modules.VoiceModule;
import skwhy.voice.VoiceListener;

@Name("Has Voice")
@Description("Checks if one or more players are currently connected and using the voice module.")
@Examples({
    "if player has voice:",
    "\tsend \"You are using voice chat!\"",
    "if all players have voice:",
    "\tsend \"Everyone is on voice chat.\""
})
@Since("1.1.0")
@RequiredPlugins({"SimpleVoiceChat"})
public class HasVoice extends Condition {

    // ── Enregistrement de la condition ─────────────────────────────────────────

    public static void register(SkriptAddon addon) {
        addon.syntaxRegistry().register(
            SyntaxRegistry.CONDITION,
            SyntaxInfo.builder(HasVoice.class)
                .origin(Origin.of(addon))
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
            if (listener.isListening(player) == isNegated()) return false;
        }
        return true;
    }

    // ── Affichage (Debug Skript) ──────────────────────────────────────────────

    @Override
    public String toString(@Nullable Event event, boolean debug) {
        return playersExpr.toString(event, debug) + (isNegated() ? " hasn't voice" : " has voice");
    }
}